package featurecreep.api.model.io.pmx;

import java.util.List;

/**
 * PMX data with no equivalent in the core model, kept under the {@code "pmx"} extension key. Bones, vertices and
 * materials are in PMX file order, and every bone index in this document refers to that order. Vectors are stored in
 * PMX (Y-up) space and are not mirrored.
 */
public record PmxDocument(
        float version,
        int additionalUvCount,
        List<Bone> bones,
        List<Vertex> vertices,
        List<Material> materials,
        List<Morph> morphs,
        List<DisplayFrame> displayFrames,
        List<RigidBody> rigidBodies,
        List<Joint> joints) {

    public PmxDocument {
        bones = List.copyOf(bones);
        vertices = List.copyOf(vertices);
        materials = List.copyOf(materials);
        morphs = List.copyOf(morphs);
        displayFrames = List.copyOf(displayFrames);
        rigidBodies = List.copyOf(rigidBodies);
        joints = List.copyOf(joints);
    }

    /** Flag bit 0x0001: tail is a bone index rather than an offset. */
    public static final int BONE_FLAG_INDEXED_TAIL = 0x0001;
    /** Flag bit 0x0020: bone carries an IK chain. */
    public static final int BONE_FLAG_IK = 0x0020;
    /** Flag bit 0x0100: bone inherits rotation from another bone (append). */
    public static final int BONE_FLAG_INHERIT_ROTATION = 0x0100;
    /** Flag bit 0x0200: bone inherits translation from another bone (append). */
    public static final int BONE_FLAG_INHERIT_TRANSLATION = 0x0200;
    /** Flag bit 0x0008: bone is visible in the editor. */
    public static final int BONE_FLAG_VISIBLE = 0x0008;
    /** Flags a bone gets when it has no PMX data yet: rotatable, translatable, visible, operable. */
    public static final int BONE_FLAG_DEFAULT = 0x001E;

    public record Bone(String name, String nameEn, int flags, int level, int tailIndex, float[] tailOffset,
            int inheritParent, float inheritRatio, float[] fixedAxis, float[] localX, float[] localZ,
            int externalParent, Ik ik) {
    }

    public record Ik(int target, int loops, float angle, List<IkLink> links) {
        public Ik {
            links = List.copyOf(links);
        }
    }

    public record IkLink(int bone, boolean limited, float[] min, float[] max) {
    }

    /** {@code skinType} follows the PMX codes: 0 BDEF1, 1 BDEF2, 2 BDEF4, 3 SDEF, 4 QDEF. {@code sdef} holds C, R0, R1. */
    public record Vertex(int skinType, float[] sdef, float edgeScale, float[] additionalUv) {
    }

    /** {@code sharedToon} is 1 when {@code toonIndex} refers to a shared toon file rather than a texture. */
    public record Material(String name, String nameEn, float[] diffuse, float[] specular, float specularity,
            float[] ambient, int flags, float[] edgeColor, float edgeSize, int sphereMode, int sharedToon,
            int toonIndex, String comment) {
    }

    /**
     * Morph entry. {@code kind} follows the PMX codes: 0 group, 1 vertex, 2 bone, 3 to 7 UV variants, 8 material.
     * {@code op} and {@code blend} are used only by material morphs.
     */
    public record Morph(String name, String nameEn, int panel, int kind, List<Item> items) {
        public Morph {
            items = List.copyOf(items);
        }

        public record Item(int index, int op, int blend, float[] values) {
        }
    }

    public record DisplayFrame(String name, String nameEn, int special, List<Element> elements) {
        public DisplayFrame {
            elements = List.copyOf(elements);
        }

        /** {@code type} is 0 for a bone and 1 for a morph. */
        public record Element(int type, int index) {
        }
    }

    public record RigidBody(String name, String nameEn, int bone, int group, int nonCollisionMask, int shape,
            float[] size, float[] position, float[] rotation, float mass, float linearDamping,
            float angularDamping, float restitution, float friction, int mode) {
    }

    public record Joint(String name, String nameEn, int kind, int rigidA, int rigidB, float[] position,
            float[] rotation, float[] moveMin, float[] moveMax, float[] rotMin, float[] rotMax,
            float[] springMove, float[] springRot) {
    }
}
