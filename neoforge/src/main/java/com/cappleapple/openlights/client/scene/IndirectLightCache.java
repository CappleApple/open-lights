package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfile;
import com.cappleapple.openlights.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Six-ray diffuse probes: no per-pixel GI ray tracing, no worker-thread world access. */
public final class IndirectLightCache {
    private ProbeGrid grid;
    private ProbeGrid far;
    private ClientLevel level;
    private long tick = Long.MIN_VALUE, revision;
    private int updated;
    private double millis;
    private List<LightDefinition> previousSources = List.of();
    private boolean previousBlockLight;
    private double previousDistance, previousIntensity;
    private long aggregateRevision = Long.MIN_VALUE;
    private static final Object BLOCK_CHANNEL=new Object();
    private GiSourceChannels nearChannels,farChannels;
    private double previousExposure;

    public void update(ClientLevel world, Vec3 camera, List<LightDefinition> sources, long now, AggregateLightCache aggregates) {
        int spacing = ClientConfig.GI_SPACING.get();
        int farSpacing = Math.max(spacing, (int)Math.ceil((net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance()*16.0 + 16) / 3));
        if (world != level || grid == null || grid.spacing != spacing || now < tick) {
            grid = new ProbeGrid(32 / spacing + 1, spacing); level = world; tick = Long.MIN_VALUE;
            far = new ProbeGrid(9, farSpacing);
            nearChannels=null;farChannels=null;
        }
        if (far.spacing != farSpacing) {far = new ProbeGrid(9, farSpacing);farChannels=null;}
        if (tick == now) return;
        long start = System.nanoTime();
        tick = now;
        ColoredLightCache.INSTANCE.begin(world, now);
        boolean changed = grid.move(camera.x, camera.y, camera.z);
        changed |= far.move(camera.x, camera.y, camera.z);
        if(nearChannels==null)nearChannels=new GiSourceChannels(grid,32L*1024*1024);else nearChannels.move();
        if(farChannels==null)farChannels=new GiSourceChannels(far,32L*1024*1024);else farChannels.move();
        boolean blockLight = ClientConfig.GI_BLOCK_LIGHT.get();
        long currentAggregate = aggregates == null ? -1 : aggregates.revision();
        boolean aggregateChanged=currentAggregate!=aggregateRevision;
        if (blockLight && aggregateChanged) { grid.invalidate(); far.invalidate(); }
        boolean sourceChange=!previousSources.equals(sources)||aggregateChanged;
        aggregateRevision = currentAggregate;
        double distance = ClientConfig.GI_TRACE_DISTANCE.get(), intensity = ClientConfig.INTENSITY_MULTIPLIER.get();
        double exposure=ClientConfig.BLOCK_LIGHT_EXPOSURE.get();
        if(previousBlockLight!=blockLight||previousDistance!=distance||previousIntensity!=intensity||previousExposure!=exposure) {
            changed|=nearChannels.clear();changed|=farChannels.clear();grid.invalidate();far.invalidate();
        }
        if (!previousSources.equals(sources) || previousBlockLight != blockLight
                || previousDistance != distance || previousIntensity != intensity) { grid.invalidate(); far.invalidate(); }
        previousSources = List.copyOf(sources);
        previousBlockLight = blockLight; previousDistance = distance; previousIntensity = intensity;
        previousExposure=exposure;
        var live=new HashSet<Object>(sources);
        Set<AnalyticBlockLighting.Emitter> liveBlock=aggregates==null?Set.of():aggregates.liveChannelKeys();
        if(blockLight) {
            if(aggregates!=null&&aggregates.analytic()&&aggregates.channels()!=null)live.addAll(liveBlock);
            else live.add(BLOCK_CHANNEL);
        }
        if(!sourceChange)live.add(GiSourceChannels.OVERFLOW);
        // Cached source columns are removed before any refresh budget or minimum-age gate.
        changed|=nearChannels.retain(live);changed|=farChannels.retain(live);
        long budget = (long)(ClientConfig.GI_BUDGET_MILLIS.get() * 1_000_000);
        updated = 0;
        for (int n = 0; n < ClientConfig.GI_PROBES_PER_TICK.get(); n++) {
            if (n > 0 && System.nanoTime() - start >= budget) break;
            ProbeGrid target = ((n + now) & 1) == 0 ? grid : far;
            int index = target.next(now, ClientConfig.GI_REFRESH_TICKS.get(), ClientConfig.PERIODIC_CACHE_REFRESH.get());
            if (index < 0) {
                target = target == grid ? far : grid;
                index = target.next(now, ClientConfig.GI_REFRESH_TICKS.get(), ClientConfig.PERIODIC_CACHE_REFRESH.get());
            }
            if (index < 0) break;
            Vec3 position = new Vec3(target.x(index), target.y(index), target.z(index));
            Vec3 value;
            try { value = (target==grid?nearChannels:farChannels).replace(index,sample(world, position, sources, aggregates,liveBlock)); }
            catch (ColoredLightCache.Pending pending) { break; }
            changed |= target.set(index, (float)value.x, (float)value.y, (float)value.z, 1, now);
            updated++;
        }
        millis = (System.nanoTime() - start) / 1_000_000.0;
        if (changed) revision++;
    }

    private static Map<Object,Vec3> sample(ClientLevel world, Vec3 probe, List<LightDefinition> sources, AggregateLightCache aggregates,Set<AnalyticBlockLighting.Emitter> liveBlock) {
        Map<Object,Vec3> contributions=new HashMap<>();
        BlockPos cell = BlockPos.containing(probe);
        if (!world.hasChunk(cell.getX() >> 4, cell.getZ() >> 4)
                || !world.getBlockState(cell).getCollisionShape(world, cell).isEmpty()) return contributions;
        double distance = ClientConfig.GI_TRACE_DISTANCE.get();
        for (Direction face : Direction.values()) {
            Vec3 direction = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 end = probe.add(direction.scale(distance));
            if (!loadedSegment(world, probe, end)) continue;
            var hit = world.clip(new ClipContext(probe, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            Vec3 receiver = hit.getLocation().add(normal.scale(.03));
            BlockPos outside = BlockPos.containing(receiver);
            var state = world.getBlockState(hit.getBlockPos());
            Vec3 albedo = TextureColors.surface(world, hit.getBlockPos(), state).scale(.65).add(.15,.15,.15);
            double weight = 1.0 / (6 * (1 + probe.distanceToSqr(receiver) * .04));
            Vec3 reflectance=albedo.scale(weight);
            if (ClientConfig.GI_BLOCK_LIGHT.get()) {
                if(aggregates!=null&&aggregates.analytic()&&aggregates.channels()!=null) {
                    for(var channel:aggregates.channels().at(outside.asLong()))if(liveBlock.contains(channel.source())) {
                        Vec3 incident=channel.sample(outside.asLong()).scale(blockScale(aggregates));
                        if(incident.lengthSqr()>0)contributions.merge(channel.source(),incident.multiply(reflectance),Vec3::add);
                    }
                } else {
                double block = world.getBrightness(LightLayer.BLOCK, outside) / 15.0;
                Vec3 incident = ColoredLightCache.INSTANCE.tint(outside).scale(block * block);
                int extra = aggregates == null ? 0 : aggregates.sample(outside);
                double aggregate = (extra >>> 24) / 255.0;
                if ((aggregates!=null&&aggregates.analytic())||aggregate > block) incident = new Vec3((extra>>16&255)/255.0,(extra>>8&255)/255.0,(extra&255)/255.0).scale(aggregate*aggregate);
                contributions.merge(BLOCK_CHANNEL,incident.multiply(reflectance).scale(blockScale(aggregates)),Vec3::add);
                }
            }
            for (LightDefinition light : sources) {
                Vec3 radiance = radiance(light, receiver);
                if (radiance.lengthSqr() < .000001) continue;
                double cosine = Math.max(0, light.position().subtract(receiver).normalize().dot(normal));
                if (cosine <= 0 || !loadedSegment(world, receiver, light.position())) continue;
                Vec3 transmission = transmission(world, receiver, light.position());
                contributions.merge(light,radiance.multiply(transmission).multiply(reflectance).scale(cosine * ClientConfig.INTENSITY_MULTIPLIER.get()),Vec3::add);
            }
        }
        return contributions;
    }
    private static double blockScale(AggregateLightCache aggregates){return ClientConfig.INTENSITY_MULTIPLIER.get()*(aggregates!=null&&aggregates.analytic()?ClientConfig.BLOCK_LIGHT_EXPOSURE.get():1);}

    /** Centre-emitter approximation for area lights; profile cones match the direct renderer. */
    public static Vec3 radiance(LightDefinition light, Vec3 receiver) {
        Vec3 delta = receiver.subtract(light.position());
        double distance = delta.length();
        if (distance >= light.range()) return Vec3.ZERO;
        double attenuation = Math.pow(1 - distance / light.range(), 2) / (1 + distance * distance * .06);
        Vec3 value = new Vec3(attenuation, attenuation, attenuation);
        if (light instanceof LightDefinition.Spot spot) {
            double cosine = spot.forward().dot(delta.normalize());
            if (spot.beamProfile() != null) {
                value = layer(spot.beamProfile().inner(), distance, cosine)
                        .add(layer(spot.beamProfile().outer(), distance, cosine));
            } else value = value.scale(smooth(cos(spot.outerConeAngleDegrees()), cos(spot.innerConeAngleDegrees()), cosine));
        } else if (light instanceof LightDefinition.Area area) {
            double outer = cos(area.spreadAngleDegrees());
            value = value.scale(smooth(outer, Math.min(1, outer + .08), area.forward().dot(delta.normalize())));
        }
        return value.multiply(light.color()).scale(light.intensity());
    }

    private static Vec3 layer(BeamProfile.Layer layer, double distance, double cosine) {
        if (distance >= layer.range()) return Vec3.ZERO;
        double shape = smooth(cos(layer.angleDegrees()), cos(layer.angleDegrees() * (1 - layer.edgeSoftness())), cosine);
        return layer.color().scale(layer.intensity() * shape * Math.pow(1 - distance / layer.range(), layer.falloff())
                / (1 + distance * distance * .06));
    }
    private static double cos(double degrees) { return Math.cos(Math.toRadians(degrees * .5)); }
    private static double smooth(double low, double high, double value) {
        if (high - low < .000001) return value >= low ? 1 : 0;
        double t = Math.max(0, Math.min(1, (value - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }
    private static boolean loadedSegment(ClientLevel world, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int)Math.ceil(start.distanceTo(end) / 8));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = start.lerp(end, i / (double)steps);
            if (!world.hasChunk((int)Math.floor(p.x) >> 4, (int)Math.floor(p.z) >> 4)) return false;
        }
        return true;
    }

    /** Walk a bounded source ray through actual medium shapes; opaque intersections block it. */
    private static Vec3 transmission(ClientLevel world, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start), color = new Vec3(1,1,1);
        double t = 0;
        for (int step = 0; step < 192 && t < 1; step++) {
            BlockPos pos = BlockPos.containing(start.add(delta.scale(Math.min(1, t + 1e-7))));
            if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return Vec3.ZERO;
            double tx = boundary(start.x, delta.x, pos.getX()), ty = boundary(start.y, delta.y, pos.getY()), tz = boundary(start.z, delta.z, pos.getZ());
            double next = Math.min(1, Math.min(tx, Math.min(ty,tz)));
            var state = world.getBlockState(pos);
            var optical = OpticalMaterials.block(world, pos, state);
            var shape = optical != null ? state.getShape(world,pos) : state.getCollisionShape(world,pos);
            for (var box : shape.toAabbs()) {
                var bounds = box.move(pos);
                Vec3 a = bounds.contains(start) ? start : bounds.clip(start,end).orElse(null);
                Vec3 b = bounds.contains(end) ? end : bounds.clip(end,start).orElse(null);
                if (a == null || b == null) continue;
                if (optical == null) {
                    if (a.distanceToSqr(end) > .75 * .75) return Vec3.ZERO;
                } else color = attenuate(color, optical, a.distanceTo(b));
            }
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))
                color = attenuate(color, OpticalMaterials.water(world,pos), Math.max(0,next-t)*delta.length());
            if (next <= t) return Vec3.ZERO;
            t = next;
        }
        return t >= 1 ? color : Vec3.ZERO;
    }
    private static double boundary(double start, double delta, int cell) {
        return Math.abs(delta) < 1e-9 ? Double.POSITIVE_INFINITY : ((delta > 0 ? cell+1 : cell)-start)/delta;
    }
    private static Vec3 attenuate(Vec3 color, OpticalMaterials.Properties medium, double distance) {
        return color.multiply(new Vec3(Math.pow(medium.tint().x*medium.throughput(), distance),
                Math.pow(medium.tint().y*medium.throughput(), distance), Math.pow(medium.tint().z*medium.throughput(), distance)));
    }
    public ProbeGrid grid() { return grid; }
    public ProbeGrid far() { return far; }
    public void invalidateRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean geometry) {
        if (grid == null || (!geometry && !previousBlockLight)) return;
        // A geometry change may obstruct either a probe ray or any surface-to-source ray.
        double reach = previousDistance + 1;
        if (geometry) for (LightDefinition source : previousSources) reach = Math.max(reach, previousDistance + source.range() + 1);
        if (grid.intersects(minX, minY, minZ, maxX, maxY, maxZ, (int)Math.ceil(reach))) grid.invalidate();
        far.invalidateRegion(minX-(int)Math.ceil(reach), minY-(int)Math.ceil(reach), minZ-(int)Math.ceil(reach),
                maxX+(int)Math.ceil(reach), maxY+(int)Math.ceil(reach), maxZ+(int)Math.ceil(reach));
    }
    public long revision() { return revision; }
    public int updated() { return updated; }
    public double millis() { return millis; }
    public int sourceChannels(){return nearChannels==null?0:nearChannels.count()+farChannels.count();}
    public long channelBytes(){return nearChannels==null?0:nearChannels.bytes()+farChannels.bytes();}
    public void clear() { grid = far = null; nearChannels=farChannels=null; level = null; tick = Long.MIN_VALUE; updated = 0; millis = 0; previousSources = List.of(); aggregateRevision=Long.MIN_VALUE; previousBlockLight=false; previousDistance=previousIntensity=previousExposure=0; revision++; }
}
