package com.cappleapple.openlights.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class GlProgram implements AutoCloseable {
    final int id;
    private final Map<String, Integer> uniforms = new HashMap<>();

    GlProgram(String vertex, String fragment) throws IOException {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex);
        int fs = 0;
        int program = 0;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IOException("Open Lights link failed: " + GL20.glGetProgramInfoLog(program));
            }
            id = program;
        } catch (IOException | RuntimeException exception) {
            if (program != 0) GL20.glDeleteProgram(program);
            throw exception;
        } finally {
            GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
    }

    private static int compile(int type, String file) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(
                new ResourceLocation("openlights", "shaders/" + file)).orElseThrow();
        String source;
        try (var input = resource.open()) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String error = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IOException("Open Lights shader " + file + ": " + error);
        }
        return shader;
    }

    void bind() { GL20.glUseProgram(id); }
    private int location(String name) { return uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(id, key)); }
    void integer(String name, int value) { GL20.glUniform1i(location(name), value); }
    void scalar(String name, float value) { GL20.glUniform1f(location(name), value); }
    void vec2(String name, float x, float y) { GL20.glUniform2f(location(name), x, y); }
    void vec3(String name, float x, float y, float z) { GL20.glUniform3f(location(name), x, y, z); }
    void vectors(String name, float[] values) { GL20.glUniform4fv(location(name), values); }
    void matrices(String name, float[] values) { GL20.glUniformMatrix4fv(location(name), false, values); }
    void matrix(String name, Matrix4f value) {
        try (var stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(location(name), false, value.get(stack.mallocFloat(16)));
        }
    }
    @Override public void close() { GL20.glDeleteProgram(id); }
}
