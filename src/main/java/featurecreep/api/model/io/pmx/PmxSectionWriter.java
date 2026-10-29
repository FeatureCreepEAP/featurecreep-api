package featurecreep.api.model.io.pmx;

/** Encodes PMX records. Each method mirrors the matching read in {@link PmxSectionReader}. */
final class PmxSectionWriter {

    private PmxSectionWriter() {
    }

    static void bone(PmxBinaryWriter w, PmxDocument.Bone b, float[] position, int parent) {
        int flags = b.flags();
        w.text(b.name());
        w.text(b.nameEn());
        w.floats(position);
        w.index(parent);
        w.s32(b.level());
        w.u8(flags & 0xFF);
        w.u8((flags >> 8) & 0xFF);
        if ((flags & PmxDocument.BONE_FLAG_INDEXED_TAIL) != 0) {
            w.index(b.tailIndex());
        } else {
            w.floats(orZero(b.tailOffset(), 3));
        }
        if ((flags & (PmxDocument.BONE_FLAG_INHERIT_ROTATION | PmxDocument.BONE_FLAG_INHERIT_TRANSLATION)) != 0) {
            w.index(b.inheritParent());
            w.f32(b.inheritRatio());
        }
        if ((flags & 0x0400) != 0) {
            w.floats(orZero(b.fixedAxis(), 3));
        }
        if ((flags & 0x0800) != 0) {
            w.floats(orZero(b.localX(), 3));
            w.floats(orZero(b.localZ(), 3));
        }
        if ((flags & 0x2000) != 0) {
            w.s32(b.externalParent());
        }
        if ((flags & PmxDocument.BONE_FLAG_IK) != 0) {
            ik(w, b.ik());
        }
    }

    private static void ik(PmxBinaryWriter w, PmxDocument.Ik ik) {
        if (ik == null) {
            w.index(-1);
            w.s32(0);
            w.f32(0f);
            w.s32(0);
            return;
        }
        w.index(ik.target());
        w.s32(ik.loops());
        w.f32(ik.angle());
        w.s32(ik.links().size());
        for (PmxDocument.IkLink link : ik.links()) {
            w.index(link.bone());
            w.u8(link.limited() ? 1 : 0);
            if (link.limited()) {
                w.floats(orZero(link.min(), 3));
                w.floats(orZero(link.max(), 3));
            }
        }
    }

    static void material(PmxBinaryWriter w, PmxDocument.Material m, int texture, int sphere, int toon, int faceCount) {
        w.text(m.name());
        w.text(m.nameEn());
        w.floats(m.diffuse());
        w.floats(m.specular());
        w.f32(m.specularity());
        w.floats(m.ambient());
        w.u8(m.flags());
        w.floats(m.edgeColor());
        w.f32(m.edgeSize());
        w.index(texture);
        w.index(sphere);
        w.u8(m.sphereMode());
        w.u8(m.sharedToon());
        if (m.sharedToon() == 1) {
            w.u8(m.toonIndex());
        } else {
            w.index(toon);
        }
        w.text(m.comment());
        w.s32(faceCount);
    }

    static void morph(PmxBinaryWriter w, PmxDocument.Morph m) {
        w.text(m.name());
        w.text(m.nameEn());
        w.u8(m.panel());
        w.u8(m.kind());
        w.s32(m.items().size());
        for (PmxDocument.Morph.Item item : m.items()) {
            w.index(item.index());
            if (m.kind() == 8) {
                w.u8(item.op());
                w.u8(item.blend());
            }
            w.floats(item.values());
        }
    }

    static void displayFrame(PmxBinaryWriter w, PmxDocument.DisplayFrame f) {
        w.text(f.name());
        w.text(f.nameEn());
        w.u8(f.special());
        w.s32(f.elements().size());
        for (PmxDocument.DisplayFrame.Element e : f.elements()) {
            w.u8(e.type());
            w.index(e.index());
        }
    }

    static void rigidBody(PmxBinaryWriter w, PmxDocument.RigidBody r) {
        w.text(r.name());
        w.text(r.nameEn());
        w.index(r.bone());
        w.u8(r.group());
        w.u8(r.nonCollisionMask() & 0xFF);
        w.u8((r.nonCollisionMask() >> 8) & 0xFF);
        w.u8(r.shape());
        w.floats(r.size());
        w.floats(r.position());
        w.floats(r.rotation());
        w.f32(r.mass());
        w.f32(r.linearDamping());
        w.f32(r.angularDamping());
        w.f32(r.restitution());
        w.f32(r.friction());
        w.u8(r.mode());
    }

    static void joint(PmxBinaryWriter w, PmxDocument.Joint j) {
        w.text(j.name());
        w.text(j.nameEn());
        w.u8(j.kind());
        w.index(j.rigidA());
        w.index(j.rigidB());
        w.floats(j.position());
        w.floats(j.rotation());
        w.floats(j.moveMin());
        w.floats(j.moveMax());
        w.floats(j.rotMin());
        w.floats(j.rotMax());
        w.floats(j.springMove());
        w.floats(j.springRot());
    }

    static float[] orZero(float[] values, int length) {
        return values != null ? values : new float[length];
    }
}
