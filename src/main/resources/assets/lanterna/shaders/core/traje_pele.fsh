#version 150

// Pixel do traje: o alfa da textura guarda QUANDO o líquido chega nele (1..255 = tempo 0..1; 0 = sem traje).
// Só aparece o que o líquido já cobriu; a beirada recém-coberta brilha na cor da Tropa.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in float sombra;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord0);
    if (c.a < 0.002) discard;
    float t = clamp((c.a * 255.0 - 1.0) / 254.0, 0.0, 1.0);
    float p = vertexColor.a;
    if (t > p) discard;
    vec4 cor = vec4(c.rgb * sombra, 1.0) * ColorModulator;
    cor.rgb = mix(overlayColor.rgb, cor.rgb, overlayColor.a);
    cor *= lightMapColor;
    // beirada do líquido: acesa na cor da Tropa (não escurece no escuro)
    float faixa = p < 0.999 ? clamp(1.0 - (p - t) / 0.09, 0.0, 1.0) : 0.0;
    vec3 tom = min(vertexColor.rgb + vec3(0.35), vec3(1.0));
    cor.rgb = mix(cor.rgb, tom, faixa * 0.85);
    cor.a = 1.0;
    if (vertexDistance > FogStart) {
        float f = vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
        cor.rgb = mix(cor.rgb, FogColor.rgb, f * FogColor.a);
    }
    fragColor = cor;
}
