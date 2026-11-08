#version 150

// Junta o halo desfocado com a silhueta original: brilho contínuo em volta do corpo inteiro,
// forte colado na borda e sumindo para fora, com uma leve ondulação viva. Por dentro do corpo
// só uma beirada fina acesa (o personagem continua visível).
uniform sampler2D DiffuseSampler; // silhueta desfocada (cor da Tropa)
uniform sampler2D MaskSampler;    // silhueta original
uniform float Time;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 b = texture(DiffuseSampler, texCoord);
    float m = texture(MaskSampler, texCoord).a;
    if (b.a < 0.003 && m < 0.01) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 cor = b.a > 0.003 ? b.rgb / b.a : texture(MaskSampler, texCoord).rgb;

    // ondulação: a borda do halo "respira" e tremula como energia
    float t = Time * 6.2831853;
    float onda = 0.5 + 0.5 * sin(texCoord.y * 140.0 - t * 3.0 + sin(texCoord.x * 95.0 + t * 2.0) * 1.7);
    float onda2 = 0.5 + 0.5 * sin(texCoord.x * 60.0 + texCoord.y * 40.0 + t * 4.0);
    float vivo = 0.82 + 0.12 * onda + 0.06 * onda2;

    // fora do corpo: halo que vai sumindo
    float fora = smoothstep(0.0, 0.40, b.a * vivo);
    fora = pow(fora, 0.9);
    // dentro do corpo: só uma beirada fina perto da borda
    float dentro = clamp((1.0 - b.a) * 2.4 - 0.45, 0.0, 1.0) * 0.45;

    float a = mix(fora, dentro, m);
    // perto da borda a luz clareia (núcleo quente), mais longe fica na cor da Tropa, bem saturada
    vec3 viva = clamp(cor * 1.35 + vec3(0.05), 0.0, 1.0);
    vec3 c = mix(viva, vec3(0.92, 1.0, 0.92) * max(viva, vec3(0.6)), clamp((b.a - 0.25) * 1.4, 0.0, 0.55));
    fragColor = vec4(clamp(c, 0.0, 1.0), clamp(a, 0.0, 1.0));
}
