# Run with: blender --background --python tools/build_mana_avatar.py -- --output assets/mana_avatar.glb
import bpy
import math
import os
import sys
from mathutils import Vector

def arg_value(name, default):
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    return args[args.index(name) + 1] if name in args else default

out = os.path.abspath(arg_value("--output", "assets/mana_avatar.glb"))
os.makedirs(os.path.dirname(out), exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
for datablocks in (bpy.data.meshes, bpy.data.curves, bpy.data.materials, bpy.data.armatures, bpy.data.actions):
    pass

def mat(name, color, metallic=0.0, roughness=0.55):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1.0)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    return m

skin = mat("Mana_Skin", (0.91, 0.73, 0.66), roughness=0.72)
hair = mat("Mana_Hair_Obsidian", (0.018, 0.025, 0.045), metallic=0.08, roughness=0.3)
cloth = mat("Mana_Clothing_Midnight", (0.025, 0.045, 0.085), metallic=0.18, roughness=0.48)
cyan = mat("Mana_Cyan_Accents", (0.04, 0.55, 0.85), metallic=0.55, roughness=0.25)
eye_mat = mat("Mana_Eyes_DeepBlue", (0.018, 0.075, 0.14), metallic=0.2, roughness=0.2)
white = mat("Mana_EyeHighlights", (0.92, 0.97, 1.0), roughness=0.2)
mouth_mat = mat("Mana_Mouth", (0.22, 0.055, 0.075), roughness=0.65)

# Bone hierarchy, in meters; Z is up, front faces toward -Y.
arm_data = bpy.data.armatures.new("Mana_Rig")
arm = bpy.data.objects.new("Mana_Rig", arm_data)
bpy.context.collection.objects.link(arm)
bpy.context.view_layer.objects.active = arm
arm.select_set(True)
bpy.ops.object.mode_set(mode="EDIT")
bones = {
    "root": ((0,0,0.0),(0,0,0.55),None),
    "spine": ((0,0,0.55),(0,0,1.28),"root"),
    "neck": ((0,0,1.28),(0,0,1.43),"spine"),
    "head": ((0,0,1.43),(0,0,1.82),"neck"),
    "upper_arm.L": ((0.18,0,1.2),(0.43,0,0.92),"spine"),
    "lower_arm.L": ((0.43,0,0.92),(0.47,0,0.62),"upper_arm.L"),
    "upper_arm.R": ((-0.18,0,1.2),(-0.43,0,0.92),"spine"),
    "lower_arm.R": ((-0.43,0,0.92),(-0.47,0,0.62),"upper_arm.R"),
    "thigh.L": ((0.105,0,0.56),(0.12,0,0.29),"root"),
    "shin.L": ((0.12,0,0.29),(0.12,0,0.05),"thigh.L"),
    "thigh.R": ((-0.105,0,0.56),(-0.12,0,0.29),"root"),
    "shin.R": ((-0.12,0,0.29),(-0.12,0,0.05),"thigh.R"),
}
for name,(head,tail,parent) in bones.items():
    b=arm_data.edit_bones.new(name); b.head=head; b.tail=tail
    if parent: b.parent=arm_data.edit_bones[parent]
bpy.ops.object.mode_set(mode="OBJECT")
arm.select_set(False)

def bind(obj, bone):
    obj.parent=arm
    mod=obj.modifiers.new("Mana_Skeleton","ARMATURE"); mod.object=arm
    group=obj.vertex_groups.new(name=bone)
    group.add(list(range(len(obj.data.vertices))),1.0,"REPLACE")
    return obj

def sphere(name, loc, scale, material, bone, segments=24, rings=16):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=loc)
    o=bpy.context.object; o.name=name; o.scale=scale; o.data.materials.append(material)
    for p in o.data.polygons: p.use_smooth=True
    return bind(o,bone)

def capsule(name, a, b, radius, material, bone):
    a,b=Vector(a),Vector(b); d=b-a
    bpy.ops.mesh.primitive_uv_sphere_add(segments=20, ring_count=12, location=(a+b)/2)
    o=bpy.context.object; o.name=name; o.scale=(radius,radius,d.length/2+radius)
    o.rotation_mode="QUATERNION"; o.rotation_quaternion=Vector((0,0,1)).rotation_difference(d.normalized())
    o.data.materials.append(material)
    for p in o.data.polygons: p.use_smooth=True
    return bind(o,bone)

# Stylized original character body and dark outfit.
sphere("Torso_Blouse",(0,0,0.94),(0.205,0.12,0.32),cloth,"spine")
sphere("Waist",(0,0,0.67),(0.17,0.105,0.14),cloth,"root")
sphere("Collar",(0,-0.005,1.245),(0.11,0.10,0.075),skin,"neck")
sphere("Neck",(0,0,1.34),(0.065,0.065,0.12),skin,"neck")
sphere("Head",(0,-0.005,1.57),(0.19,0.16,0.245),skin,"head",32,24)
# Long hair cap and back locks.
sphere("Hair_Cap",(0,0.018,1.70),(0.202,0.174,0.18),hair,"head",32,20)
for side in (-1,1):
    capsule("Long_Hair_Lock", (side*0.16,0.035,1.62),(side*0.18,0.06,0.82),0.065,hair,"head")
    sphere("Hair_Tip",(side*0.18,0.06,0.82),(0.067,0.07,0.09),hair,"head")
# Fringe strands across forehead.
for i in range(5):
    x=-0.13+i*0.065
    capsule("Fringe",(x,-0.145,1.76),(x+0.018,-0.16,1.60+0.025*(i%2)),0.028,hair,"head")
# Face, eyes and facial morph targets.
for side in (-1,1):
    eye=sphere("Eye.L" if side>0 else "Eye.R",(side*0.073,-0.151,1.60),(0.035,0.018,0.046),eye_mat,"head")
    eye.shape_key_add(name="Basis")
    blink=eye.shape_key_add(name="Blink")
    for v in blink.data: v.co.z *= 0.08
    eye.data.shape_keys.key_blocks["Blink"].slider_min=0.0
    eye.data.shape_keys.key_blocks["Blink"].slider_max=1.0
    sphere("EyeHighlight",(side*0.083,-0.17,1.617),(0.009,0.006,0.012),white,"head",12,8)
mouth=sphere("Mouth",(0,-0.164,1.485),(0.026,0.009,0.009),mouth_mat,"head",16,8)
mouth.shape_key_add(name="Basis")
open_key=mouth.shape_key_add(name="MouthOpen")
for v in open_key.data: v.co.z *= 2.8
# Arms, gloves and legs.
for side, suffix in ((1,"L"),(-1,"R")):
    capsule("Sleeve_"+suffix,(side*0.16,0,1.19),(side*0.39,0,0.94),0.075,cloth,"upper_arm."+suffix)
    capsule("Forearm_"+suffix,(side*0.39,0,0.94),(side*0.45,-0.005,0.68),0.045,skin,"lower_arm."+suffix)
    sphere("Hand_"+suffix,(side*0.46,-0.005,0.64),(0.05,0.04,0.065),skin,"lower_arm."+suffix)
    capsule("Leg_"+suffix,(side*0.105,0,0.58),(side*0.12,0,0.25),0.07,cloth,"thigh."+suffix)
    capsule("Boot_"+suffix,(side*0.12,0,0.27),(side*0.12,-0.015,0.06),0.055,cloth,"shin."+suffix)
    sphere("BootToe_"+suffix,(side*0.12,-0.055,0.055),(0.07,0.12,0.045),hair,"shin."+suffix)
# Cyan collar pin and belt accents.
sphere("Mana_Core_Pin",(0,-0.125,1.19),(0.025,0.012,0.025),cyan,"spine",16,8)
sphere("Mana_Belt",(0,-0.105,0.69),(0.11,0.012,0.018),cyan,"root",16,8)

# A subtle idle action, with no autonomous locomotion.
bpy.context.view_layer.objects.active=arm
arm.animation_data_create()
action=bpy.data.actions.new("Mana_Idle")
arm.animation_data.action=action
for bone_name, amplitude in (("spine",0.018),("head",0.012)):
    pb=arm.pose.bones[bone_name]
    pb.rotation_mode="XYZ"
    for frame, angle in ((1,-amplitude),(20,amplitude),(40,-amplitude)):
        pb.rotation_euler[1]=angle
        pb.keyframe_insert(data_path="rotation_euler",frame=frame,group=bone_name)
for fc in action.fcurves:
    for kp in fc.keyframe_points: kp.interpolation="BEZIER"
    fc.modifiers.new("CYCLES")
scene=bpy.context.scene
scene.frame_start=1; scene.frame_end=40; scene.render.fps=30

# Export only the character and rig; model is an original procedural MANA asset.
bpy.ops.object.select_all(action="DESELECT")
for obj in bpy.context.scene.objects:
    if obj.type in {"MESH","ARMATURE"}: obj.select_set(True)
bpy.context.view_layer.objects.active=arm
bpy.ops.export_scene.gltf(filepath=out, export_format="GLB", export_animations=True, export_skins=True, export_morph=True, export_materials="EXPORT")
print("MANA_AVATAR_EXPORTED=" + out)
