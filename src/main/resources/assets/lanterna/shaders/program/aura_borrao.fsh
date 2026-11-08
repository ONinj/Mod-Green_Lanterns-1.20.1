#version 150

// Desfoque gaussiano numa direção (a aura do anel: a silhueta vira um halo macio)
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float Radius;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    // o raio cresce um pouco com a resolução (fica parecido em 720p e 1440p)
    float r = Radius * max(1.0, InSize.y / 720.0);
    float sigma = r * 0.5;
    vec4 soma = vec4(0.0);
    float peso = 0.0;
    for (float i = -r; i <= r; i += 1.0) {
        float w = exp(-(i * i) / (2.0 * sigma * sigma));
        soma += texture(DiffuseSampler, texCoord + oneTexel * i * BlurDir) * w;
        peso += w;
    }
    fragColor = soma / peso;
}
