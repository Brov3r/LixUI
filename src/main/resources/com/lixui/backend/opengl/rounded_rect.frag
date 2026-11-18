#version 330 core
in vec2 vLocal;
in vec2 vHalfSize;
in float vRadius;
in vec4 vColor;
in vec2 vUv;
uniform sampler2D uTexture;
uniform vec4 uPaint[7];
out vec4 fragColor;
float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
    vec2 offset = abs(point) - halfSize + radius;
    return length(max(offset, 0.0)) + min(max(offset.x, offset.y), 0.0) - radius;
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
    int count = int(uPaint[6].y);
    if (position <= uPaint[5][0]) {
        return uPaint[1];
    }
    for (int i = 1; i < count; i++) {
        if (position < uPaint[5][i]) {
            float amount = (position - uPaint[5][i - 1]) / (uPaint[5][i] - uPaint[5][i - 1]);
            return mix(uPaint[i], uPaint[i + 1], amount);
        }
    }
    return uPaint[count];
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
