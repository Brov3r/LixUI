#version 450
layout (location = 0) in vec2 aPosition;
layout (location = 1) in vec2 aLocal;
layout (location = 2) in vec2 aHalfSize;
layout (location = 3) in float aRadius;
layout (location = 4) in vec4 aColor;
layout (location = 5) in vec2 aUv;
layout (push_constant) uniform PushConstants {
    vec4 viewport;
    vec4 paint[7];
} pc;
layout (location = 0) out vec2 vLocal;
layout (location = 1) out vec2 vHalfSize;
layout (location = 2) out float vRadius;
layout (location = 3) out vec4 vColor;
layout (location = 4) out vec2 vUv;
void main() {
    vec2 ndc = vec2(aPosition.x / pc.viewport.x * 2.0 - 1.0,
                    aPosition.y / pc.viewport.y * 2.0 - 1.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    vLocal = aLocal;
    vHalfSize = aHalfSize;
    vRadius = aRadius;
    vColor = aColor;
    vUv = aUv;
}
