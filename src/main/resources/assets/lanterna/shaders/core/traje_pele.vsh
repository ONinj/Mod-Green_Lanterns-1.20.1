#version 150

// O traje pintado na skin do jogador. A cor do vértice traz: rgb = cor da Tropa (para a beirada que brilha),
// alfa = progresso da transformação (0..1).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out float sombra;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

#define MINECRAFT_LIGHT_POWER   (0.6)
#define MINECRAFT_AMBIENT_LIGHT (0.4)

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec3 pos = IViewRotMat * Position;
    if (FogShape == 0) vertexDistance = length(pos);
    else vertexDistance = max(length(pos.xz), abs(pos.y));
    vec3 n = normalize(Normal);
    float l0 = max(0.0, dot(normalize(Light0_Direction), n));
    float l1 = max(0.0, dot(normalize(Light1_Direction), n));
    sombra = min(1.0, (l0 + l1) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    vertexColor = Color;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
