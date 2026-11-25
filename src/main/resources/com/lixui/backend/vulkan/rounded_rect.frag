#version 450
layout (location = 0) in vec2 vLocal;
layout (location = 1) in vec2 vHalfSize;
layout (location = 2) in float vRadius;
layout (location = 3) in vec4 vColor;
layout (location = 4) in vec2 vUv;
layout (set = 0, binding = 0) uniform sampler2D uTexture;
layout (push_constant) uniform PushConstants {
    vec4 viewport;
    vec4 paint[7];
} pc;
#define uPaint pc.paint
layout (location = 0) out vec4 fragColor;
float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
    vec2 offset = abs(point) - halfSize + radius;
    return length(max(offset, 0.0)) + min(max(offset.x, offset.y), 0.0) - radius;
}
// Stops are selected without an indexed loop: some drivers miscompile dynamic
// push-constant indexing with early returns and produce tile artifacts.
vec4 gradientColor(float position) {
    int count = int(uPaint[6].y);
    float first = uPaint[5][0];
    float second = uPaint[5][1];
    float third = uPaint[5][2];
    float fourth = uPaint[5][3];
    vec4 result = uPaint[1];
    float span = second - first;
    if (position >= first) {
        float amount = span > 0.0 ? clamp((position - first) / span, 0.0, 1.0) : 1.0;
        result = mix(uPaint[1], uPaint[2], amount);
    }
    span = third - second;
    if (count > 2 && position >= second) {
        float amount = span > 0.0 ? clamp((position - second) / span, 0.0, 1.0) : 1.0;
        result = mix(uPaint[2], uPaint[3], amount);
    }
    span = fourth - third;
    if (count > 3 && position >= third) {
        float amount = span > 0.0 ? clamp((position - third) / span, 0.0, 1.0) : 1.0;
        result = mix(uPaint[3], uPaint[4], amount);
    }
    if (count == 2 && position >= second) {
        result = uPaint[2];
    }
    if (count == 3 && position >= third) {
        result = uPaint[3];
    }
    if (count > 3 && position >= fourth) {
        result = uPaint[4];
    }
    return result;
}
vec4 paintColor() {
    int type = int(uPaint[6].x);
    if (type == 0) {
        return texture(uTexture, vUv) * vColor;
    }
    vec2 delta = vUv - uPaint[0].xy;
    vec2 axis = uPaint[0].zw - uPaint[0].xy;
    float position = type == 1 ? dot(delta, axis) / dot(axis, axis)
                              : length(delta) / uPaint[0].z;
    return gradientColor(position);
}
void main() {
    float distance = vRadius < 0.0 ? length(vLocal / vHalfSize) - 1.0
                                  : roundedBoxSdf(vLocal, vHalfSize, vRadius);
    float antialias = max(fwidth(distance), 0.0001);
    float coverage = clamp(0.5 - distance / antialias, 0.0, 1.0);
    vec4 color = paintColor();
    float alpha = color.a * coverage;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(color.rgb * alpha, alpha);
}
