package featurecreep.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between a format's native space and model space, which is Minecraft model space (Y down). Every reader and
 * writer applies the same Y reflection, so a model imported from any format is consistent. A reflection flips triangle
 * winding, tangent handedness, and quaternion axes, and this class handles all three.
 */
public final class Space {

    private Space() {
    }

    public static Vec3 mirrorY(Vec3 v) {
        return new Vec3(v.x(), -v.y(), v.z());
    }

    public static Quat mirrorY(Quat q) {
        return new Quat(-q.x(), q.y(), -q.z(), q.w());
    }

    public static Transform mirrorY(Transform t) {
        return new Transform(mirrorY(t.translation()), mirrorY(t.rotation()), t.scale());
    }

    public static Cuboid mirrorY(Cuboid c) {
        Vec3 min = new Vec3(c.min().x(), -(c.min().y() + c.size().y()), c.min().z());
        return new Cuboid(min, c.size(), c.inflate(), c.uv(), c.mirror());
    }

    public static Mesh mirrorY(Mesh m) {
        float[] positions = m.positions().clone();
        for (int i = 1; i < positions.length; i += 3) {
            positions[i] = -positions[i];
        }
        float[] normals = null;
        if (m.normals() != null) {
            normals = m.normals().clone();
            for (int i = 1; i < normals.length; i += 3) {
                normals[i] = -normals[i];
            }
        }
        float[] tangents = null;
        if (m.tangents() != null) {
            tangents = m.tangents().clone();
            for (int i = 0; i < tangents.length; i += 4) {
                tangents[i + 1] = -tangents[i + 1];
                tangents[i + 3] = -tangents[i + 3];
            }
        }
        List<Mesh.Primitive> primitives = new ArrayList<>(m.primitives().size());
        for (Mesh.Primitive p : m.primitives()) {
            int[] indices = p.indices().clone();
            for (int i = 0; i < indices.length; i += 3) {
                int swap = indices[i + 1];
                indices[i + 1] = indices[i + 2];
                indices[i + 2] = swap;
            }
            primitives.add(new Mesh.Primitive(p.material(), indices));
        }
        return new Mesh(positions, normals, tangents, m.uvSets(), primitives, m.skin());
    }

    public static Geometry mirrorY(Geometry g) {
        if (g == null) {
            return null;
        }
        return switch (g) {
            case Cuboid c -> mirrorY(c);
            case Mesh m -> mirrorY(m);
            case Parametric p -> throw new UnsupportedOperationException(
                    "parametric geometry must be tessellated before mirroring");
        };
    }

    public static Node mirrorY(Node n) {
        List<Node> children = new ArrayList<>(n.children().size());
        for (Node child : n.children()) {
            children.add(mirrorY(child));
        }
        return new Node(n.name(), mirrorY(n.transform()), children, mirrorY(n.geometry()), n.visible(), n.metadata());
    }

    public static Model mirrorY(Model model) {
        List<Node> roots = new ArrayList<>(model.roots().size());
        for (Node root : model.roots()) {
            roots.add(mirrorY(root));
        }
        return new Model(roots, model.materials(), model.morphNames(), model.capabilities(), model.metadata(),
                model.extensions());
    }
}
