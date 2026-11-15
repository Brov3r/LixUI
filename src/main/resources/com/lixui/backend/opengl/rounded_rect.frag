#version 330 core
in vec2 vLocal;
in vec2 vHalfSize;
in float vRadius;
in vec4 vColor;
in vec2 vUv;
uniform sampler2D uTexture;
out vec4 fragColor;
float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
    vec2 offset = abs(point) - halfSize + radius;
    return length(max(offset, 0.0)) + min(max(offset.x, offset.y), 0.0) - radius;
}
void main() {
    float distance = roundedBoxSdf(vLocal, vHalfSize, vRadius);
    float antialias = max(fwidth(distance), 0.0001);
    float coverage = clamp(0.5 - distance / antialias, 0.0, 1.0);
    vec4 color = texture(uTexture, vUv) * vColor;
    float alpha = color.a * coverage;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(color.rgb * alpha, alpha);
}
