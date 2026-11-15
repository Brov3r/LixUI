#version 450
layout (location = 0) in vec2 vLocal;
layout (location = 1) in vec2 vHalfSize;
layout (location = 2) in float vRadius;
layout (location = 3) in vec4 vColor;
layout (location = 0) out vec4 fragColor;
float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
    vec2 offset = abs(point) - halfSize + radius;
    return length(max(offset, 0.0)) + min(max(offset.x, offset.y), 0.0) - radius;
}
void main() {
    float distance = roundedBoxSdf(vLocal, vHalfSize, vRadius);
    float antialias = max(fwidth(distance), 0.0001);
    float coverage = clamp(0.5 - distance / antialias, 0.0, 1.0);
    float alpha = vColor.a * coverage;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vColor.rgb * alpha, alpha);
}
