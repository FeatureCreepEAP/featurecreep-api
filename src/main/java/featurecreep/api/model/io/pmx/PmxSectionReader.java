package featurecreep.api.model.io.pmx;

import featurecreep.api.model.ModelFormatException;
import java.util.ArrayList;
import java.util.List;

/** Parses PMX sections in file order. Index validity against bone or vertex counts is checked by the caller. */
final class PmxSectionReader {

    static final int SKIN_BDEF1 = 0;
    static final int SKIN_BDEF2 = 1;
    static final int SKIN_BDEF4 = 2;
    static final int SKIN_SDEF = 3;
    static final int SKIN_QDEF = 4;

    private final PmxBinaryReader in;

    PmxSectionReader(PmxBinaryReader in) {
        this.in = in;
    }

    /** Header fields that size the rest of the file. */
    record Header(float version, int encoding, int additionalUvCount, int vertexIndexSize, int textureIndexSize,
            int materialIndexSize, int boneIndexSize, int morphIndexSize, int rigidIndexSize) {
    }

    /** Vertices as flat arrays for the core model, plus the PMX-only fields. Joint slots hold raw PMX bone indices. */
    record Vertices(float[] positions, float[] normals, float[] uv, float[] additionalUv, int[] joints,
            float[] weights, List<PmxDocument.Vertex> extras, boolean hasSdef, boolean hasQdef) {
    }

    /** A bone with its absolute PMX position and raw parent index. */
    record BoneRaw(PmxDocument.Bone bone, float[] position, int parent) {
    }

    record Morphs(List<PmxDocument.Morph> morphs) {
    }

    Header header() throws ModelFormatException {
        byte[] magic = in.bytes(4);
        if (magic[0] != 'P' || magic[1] != 'M' || magic[2] != 'X' || magic[3] != ' ') {
            throw in.fail("missing PMX signature");
        }
        float version = in.f32();
        if (Float.compare(version, 2.0f) != 0 && Float.compare(version, 2.1f) != 0) {
            throw in.fail("unsupported PMX version " + version);
        }
        int globalsCount = in.u8();
        if (globalsCount < 8) {
            throw in.fail("PMX globals count " + globalsCount + " is below 8");
        }
        byte[] g = in.bytes(globalsCount);
        int encoding = g[0] & 0xFF;
        if (encoding > 1) {
            throw in.fail("unknown text encoding " + encoding);
        }
        int vertexSize = indexSize(g[2]);
        int textureSize = indexSize(g[3]);
        int materialSize = indexSize(g[4]);
        int boneSize = indexSize(g[5]);
        int morphSize = indexSize(g[6]);
        int rigidSize = indexSize(g[7]);
        int addUv = g[1] & 0xFF;
        if (addUv > 4) {
            throw in.fail("additional UV count " + addUv + " exceeds 4");
        }
        return new Header(version, encoding, addUv, vertexSize, textureSize, materialSize, boneSize, morphSize,
                rigidSize);
    }

    private int indexSize(byte raw) throws ModelFormatException {
        int size = raw & 0xFF;
        if (size != 1 && size != 2 && size != 4) {
            throw in.fail("index size " + size + " is not 1, 2 or 4");
        }
        return size;
    }

    String text(Header h) throws ModelFormatException {
        return in.text(h.encoding());
    }

    Vertices vertices(Header h) throws ModelFormatException {
        int count = in.count(32);
        float[] pos = new float[count * 3];
        float[] nrm = new float[count * 3];
        float[] uv = new float[count * 2];
        float[] addUv = new float[count * h.additionalUvCount() * 4];
        int[] joints = new int[count * 4];
        float[] weights = new float[count * 4];
        List<PmxDocument.Vertex> extras = new ArrayList<>(count);
        boolean hasSdef = false;
        boolean hasQdef = false;
        for (int v = 0; v < count; v++) {
            System.arraycopy(in.floats(3), 0, pos, v * 3, 3);
            System.arraycopy(in.floats(3), 0, nrm, v * 3, 3);
            System.arraycopy(in.floats(2), 0, uv, v * 2, 2);
            int addLen = h.additionalUvCount() * 4;
            float[] add = in.floats(addLen);
            System.arraycopy(add, 0, addUv, v * addLen, addLen);
            int type = in.u8();
            float[] sdef = null;
            int base = v * 4;
            switch (type) {
                case SKIN_BDEF1 -> {
                    joints[base] = in.index(h.boneIndexSize(), true);
                    weights[base] = 1f;
                    setUnused(joints, weights, base, 1);
                }
                case SKIN_BDEF2 -> {
                    readPair(h, joints, weights, base);
                }
                case SKIN_SDEF -> {
                    hasSdef = true;
                    readPair(h, joints, weights, base);
                    sdef = in.floats(9);
                }
                case SKIN_BDEF4, SKIN_QDEF -> {
                    hasQdef |= type == SKIN_QDEF;
                    for (int k = 0; k < 4; k++) {
                        joints[base + k] = in.index(h.boneIndexSize(), true);
                    }
                    System.arraycopy(in.floats(4), 0, weights, base, 4);
                }
                default -> throw in.fail("unknown vertex skin type " + type);
            }
            float edge = in.f32();
            extras.add(new PmxDocument.Vertex(type, sdef, edge, slice(addUv, v * addLen, addLen)));
        }
        return new Vertices(pos, nrm, uv, addUv, joints, weights, extras, hasSdef, hasQdef);
    }

    private void readPair(Header h, int[] joints, float[] weights, int base) throws ModelFormatException {
        joints[base] = in.index(h.boneIndexSize(), true);
        joints[base + 1] = in.index(h.boneIndexSize(), true);
        float w = in.f32();
        weights[base] = w;
        weights[base + 1] = 1f - w;
        setUnused(joints, weights, base, 2);
    }

    private static void setUnused(int[] joints, float[] weights, int base, int used) {
        for (int k = used; k < 4; k++) {
            joints[base + k] = -1;
            weights[base + k] = 0f;
        }
    }

    private static float[] slice(float[] src, int from, int length) {
        float[] out = new float[length];
        System.arraycopy(src, from, out, 0, length);
        return out;
    }

    int[] faces(Header h) throws ModelFormatException {
        int count = in.count(h.vertexIndexSize());
        if (count % 3 != 0) {
            throw in.fail("face index count " + count + " is not a multiple of 3");
        }
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = in.index(h.vertexIndexSize(), false);
        }
        return out;
    }

    List<String> textures(Header h) throws ModelFormatException {
        int count = in.count(4);
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(text(h));
        }
        return out;
    }

    record MaterialRaw(PmxDocument.Material material, int texture, int sphere, int toon, int faceCount) {
    }

    List<MaterialRaw> materials(Header h) throws ModelFormatException {
        int count = in.count(4);
        List<MaterialRaw> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = text(h);
            String nameEn = text(h);
            float[] diffuse = in.floats(4);
            float[] specular = in.floats(3);
            float specularity = in.f32();
            float[] ambient = in.floats(3);
            int flags = in.u8();
            float[] edgeColor = in.floats(4);
            float edgeSize = in.f32();
            int texture = in.index(h.textureIndexSize(), true);
            int sphere = in.index(h.textureIndexSize(), true);
            int sphereMode = in.u8();
            int sharedToon = in.u8();
            int toon = sharedToon == 1 ? in.u8() : in.index(h.textureIndexSize(), true);
            String comment = text(h);
            int faceCount = in.s32();
            if (faceCount < 0 || faceCount % 3 != 0) {
                throw in.fail("material face count " + faceCount + " is invalid");
            }
            PmxDocument.Material material = new PmxDocument.Material(name, nameEn, diffuse, specular, specularity,
                    ambient, flags, edgeColor, edgeSize, sphereMode, sharedToon, toon, comment);
            out.add(new MaterialRaw(material, texture, sphere, toon, faceCount));
        }
        return out;
    }

    List<BoneRaw> bones(Header h) throws ModelFormatException {
        int count = in.count(20);
        List<BoneRaw> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = text(h);
            String nameEn = text(h);
            float[] position = in.floats(3);
            int parent = in.index(h.boneIndexSize(), true);
            int level = in.s32();
            int flags = in.index(2, false);
            int tailIndex = -1;
            float[] tailOffset = null;
            if ((flags & PmxDocument.BONE_FLAG_INDEXED_TAIL) != 0) {
                tailIndex = in.index(h.boneIndexSize(), true);
            } else {
                tailOffset = in.floats(3);
            }
            int inheritParent = -1;
            float inheritRatio = 0f;
            if ((flags & (PmxDocument.BONE_FLAG_INHERIT_ROTATION | PmxDocument.BONE_FLAG_INHERIT_TRANSLATION)) != 0) {
                inheritParent = in.index(h.boneIndexSize(), true);
                inheritRatio = in.f32();
            }
            float[] fixedAxis = (flags & 0x0400) != 0 ? in.floats(3) : null;
            float[] localX = null;
            float[] localZ = null;
            if ((flags & 0x0800) != 0) {
                localX = in.floats(3);
                localZ = in.floats(3);
            }
            int externalParent = (flags & 0x2000) != 0 ? in.s32() : 0;
            PmxDocument.Ik ik = null;
            if ((flags & PmxDocument.BONE_FLAG_IK) != 0) {
                ik = ik(h);
            }
            PmxDocument.Bone bone = new PmxDocument.Bone(name, nameEn, flags, level, tailIndex, tailOffset,
                    inheritParent, inheritRatio, fixedAxis, localX, localZ, externalParent, ik);
            out.add(new BoneRaw(bone, position, parent));
        }
        return out;
    }

    private PmxDocument.Ik ik(Header h) throws ModelFormatException {
        int target = in.index(h.boneIndexSize(), true);
        int loops = in.s32();
        float angle = in.f32();
        int linkCount = in.count(1 + h.boneIndexSize());
        List<PmxDocument.IkLink> links = new ArrayList<>(linkCount);
        for (int k = 0; k < linkCount; k++) {
            int bone = in.index(h.boneIndexSize(), true);
            boolean limited = in.u8() != 0;
            float[] min = limited ? in.floats(3) : null;
            float[] max = limited ? in.floats(3) : null;
            links.add(new PmxDocument.IkLink(bone, limited, min, max));
        }
        return new PmxDocument.Ik(target, loops, angle, links);
    }

    Morphs morphs(Header h) throws ModelFormatException {
        int count = in.count(8);
        List<PmxDocument.Morph> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = text(h);
            String nameEn = text(h);
            int panel = in.u8();
            int kind = in.u8();
            int itemCount = in.count(4);
            List<PmxDocument.Morph.Item> items = new ArrayList<>(itemCount);
            for (int k = 0; k < itemCount; k++) {
                items.add(morphItem(h, kind));
            }
            out.add(new PmxDocument.Morph(name, nameEn, panel, kind, items));
        }
        return new Morphs(out);
    }

    private PmxDocument.Morph.Item morphItem(Header h, int kind) throws ModelFormatException {
        return switch (kind) {
            case 0 -> new PmxDocument.Morph.Item(in.index(h.morphIndexSize(), true), 0, 0, in.floats(1));
            case 1 -> new PmxDocument.Morph.Item(in.index(h.vertexIndexSize(), false), 0, 0, in.floats(3));
            case 2 -> {
                int bone = in.index(h.boneIndexSize(), true);
                yield new PmxDocument.Morph.Item(bone, 0, 0, in.floats(7));
            }
            case 3, 4, 5, 6, 7 -> new PmxDocument.Morph.Item(in.index(h.vertexIndexSize(), false), 0, 0, in.floats(4));
            case 8 -> {
                int material = in.index(h.materialIndexSize(), true);
                int op = in.u8();
                int blend = in.u8();
                yield new PmxDocument.Morph.Item(material, op, blend, in.floats(28));
            }
            default -> throw in.fail("unknown morph kind " + kind);
        };
    }

    List<PmxDocument.DisplayFrame> displayFrames(Header h) throws ModelFormatException {
        int count = in.count(8);
        List<PmxDocument.DisplayFrame> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = text(h);
            String nameEn = text(h);
            int special = in.u8();
            int elementCount = in.count(2);
            List<PmxDocument.DisplayFrame.Element> elements = new ArrayList<>(elementCount);
            for (int k = 0; k < elementCount; k++) {
                int type = in.u8();
                int index = switch (type) {
                    case 0 -> in.index(h.boneIndexSize(), true);
                    case 1 -> in.index(h.morphIndexSize(), true);
                    default -> throw in.fail("unknown display frame element type " + type);
                };
                elements.add(new PmxDocument.DisplayFrame.Element(type, index));
            }
            out.add(new PmxDocument.DisplayFrame(name, nameEn, special, elements));
        }
        return out;
    }

    List<PmxDocument.RigidBody> rigidBodies(Header h) throws ModelFormatException {
        int count = in.count(8);
        List<PmxDocument.RigidBody> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = text(h);
            String nameEn = text(h);
            int bone = in.index(h.boneIndexSize(), true);
            int group = in.u8();
            int mask = in.index(2, false);
            int shape = in.u8();
            float[] size = in.floats(3);
            float[] position = in.floats(3);
            float[] rotation = in.floats(3);
            float mass = in.f32();
            float linear = in.f32();
            float angular = in.f32();
            float restitution = in.f32();
            float friction = in.f32();
            int mode = in.u8();
            out.add(new PmxDocument.RigidBody(name, nameEn, bone, group, mask, shape, size, position, rotation,
                    mass, linear, angular, restitution, friction, mode));
        }
        return out;
    }

    List<PmxDocument.Joint> joints(Header h) throws ModelFormatException {
        int count = in.count(8);
        List<PmxDocument.Joint> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = text(h);
            String nameEn = text(h);
            int kind = in.u8();
            int rigidA = in.index(h.rigidIndexSize(), true);
            int rigidB = in.index(h.rigidIndexSize(), true);
            out.add(new PmxDocument.Joint(name, nameEn, kind, rigidA, rigidB, in.floats(3), in.floats(3),
                    in.floats(3), in.floats(3), in.floats(3), in.floats(3), in.floats(3), in.floats(3)));
        }
        return out;
    }
}
