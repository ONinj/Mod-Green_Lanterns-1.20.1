# Converte .geo.json (bedrock/GeckoLib) em .bbmodel do Blockbench (formato "Bedrock/GeckoLib", UV por face)
import json, uuid, base64, io

def U(): return str(uuid.uuid4())

def geo_to_bbmodel(geo, textures, name, path):
    """textures: lista de (nome_arquivo, PIL.Image). A primeira é a usada nas faces."""
    G = geo["minecraft:geometry"][0]; W, H = G["description"]["texture_width"], G["description"]["texture_height"]
    elements, groups, children_of = [], {}, {}
    for b in G["bones"]:
        px, py, pz = b["pivot"]
        grp = {"name": b["name"], "origin": [-px, py, pz], "rotation": [0, 0, 0], "color": 0, "uuid": U(), "export": True,
               "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0, "children": []}
        rot = b.get("rotation")
        if rot: grp["rotation"] = [-rot[0], -rot[1], rot[2]]
        groups[b["name"]] = (grp, b.get("parent"))
        for i, c in enumerate(b.get("cubes", [])):
            ox, oy, oz = c["origin"]; sx, sy, sz = c["size"]; inf = c.get("inflate", 0)
            faces = {}
            for f, d in c["uv"].items():
                u, v = d["uv"]; w, h = d["uv_size"]
                faces[f] = {"uv": [u + w, v + h, u, v] if f in ("up", "down") else [u, v, u + w, v + h], "texture": 0}
            el = {"name": f"{b['name']}_{i + 1}", "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
                  "allow_mirror_modeling": True, "from": [-(ox + sx), oy, oz], "to": [-ox, oy + sy, oz + sz], "autouv": 0, "color": 0,
                  "inflate": inf, "origin": [-px, py, pz], "faces": faces, "type": "cube", "uuid": U()}
            if "rotation" in c:
                r = c["rotation"]; pv = c.get("pivot", [px, py, pz])
                el["rotation"] = [-r[0], -r[1], r[2]]; el["origin"] = [-pv[0], pv[1], pv[2]]
            elements.append(el); grp["children"].append(el["uuid"])
    # hierarquia
    roots = []
    for nm, (grp, parent) in groups.items():
        if parent and parent in groups: groups[parent][0]["children"].append(grp)
        else: roots.append(grp)
    texs = []
    for i, (fname, img) in enumerate(textures):
        buf = io.BytesIO(); img.save(buf, "PNG")
        texs.append({"path": "", "name": fname, "folder": "", "namespace": "", "id": str(i), "width": W, "height": H, "uv_width": W, "uv_height": H,
                     "particle": i == 0, "use_as_default": i == 0, "layers_enabled": False, "render_mode": "default", "render_sides": "auto",
                     "visible": True, "internal": True, "saved": False, "uuid": U(), "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()})
    data = {"meta": {"format_version": "4.5", "model_format": "bedrock", "box_uv": False}, "name": name, "model_identifier": name,
            "visible_box": [G["description"]["visible_bounds_width"], G["description"]["visible_bounds_height"], 0],
            "variable_placeholders": "", "variable_placeholder_buttons": [], "bedrock_animation_mode": "entity", "timeline_setups": [],
            "unhandled_root_fields": {}, "resolution": {"width": W, "height": H}, "elements": elements, "outliner": roots, "textures": texs}
    json.dump(data, open(path, "w"), indent=1)
