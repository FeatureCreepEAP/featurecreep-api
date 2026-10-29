package featurecreep.api.model.io.aimcube;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.CuboidUv;
import featurecreep.api.model.Geometry;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelCapabilities;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Space;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.FormatDescriptor;
import featurecreep.api.model.io.ModelReadResult;
import featurecreep.api.model.io.ModelReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads AIMCUBE v1 block models. AIMCUBE is written in the viewer's Y-up-positive convention, so the result is mirrored
 * into model space with {@link Space#mirrorY(Model)}.
 */
public final class AimCubeReader implements ModelReader {

    public static final String FORMAT_ID = "aimcube";
    public static final String TEXTURE_WIDTH = "texture_width";
    public static final String TEXTURE_HEIGHT = "texture_height";

    private static final String MAGIC = "aimcube:";
    private static final Pattern PART = Pattern.compile("^part\\s+(\\S+)$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern KEY = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*[:=]?\\s*(.*)$");
    private static final Pattern PAIR = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*[:=]\\s*(\\[[^\\]]*\\]|[^\\s\\[\\]]+)");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            FORMAT_ID, Set.of("aimcube"), AimCubeReader::sniff);

    @Override
    public FormatDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ModelReadResult read(byte[] data, String sourceName) throws ModelFormatException {
        String text = decode(data, sourceName);
        return new Parser(sourceName).parse(text);
    }

    static boolean sniff(byte[] header) {
        String text = new String(header, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return text.stripLeading().startsWith(MAGIC);
    }

    private static String decode(byte[] data, String sourceName) throws ModelFormatException {
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            String text = chars.toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new ModelFormatException(sourceName + ": file is not valid UTF-8");
        }
    }

    private static Quat quatFromEuler(Vec3 r) {
        return multiply(multiply(axis(r.x(), 1, 0, 0), axis(r.y(), 0, 1, 0)), axis(r.z(), 0, 0, 1));
    }

    private static Quat axis(float angle, float ax, float ay, float az) {
        double half = angle / 2.0;
        double s = Math.sin(half);
        return new Quat((float) (ax * s), (float) (ay * s), (float) (az * s), (float) Math.cos(half));
    }

    private static Quat multiply(Quat a, Quat b) {
        return new Quat(
                a.w() * b.x() + a.x() * b.w() + a.y() * b.z() - a.z() * b.y(),
                a.w() * b.y() - a.x() * b.z() + a.y() * b.w() + a.z() * b.x(),
                a.w() * b.z() + a.x() * b.y() - a.y() * b.x() + a.z() * b.w(),
                a.w() * b.w() - a.x() * b.x() - a.y() * b.y() - a.z() * b.z());
    }

    private static final class PartDraft {
        final String name;
        final int line;
        String parent;
        Vec3 pivot;
        Vec3 rotation = Vec3.ZERO;
        boolean visible = true;
        final List<CubeDraft> cubes = new ArrayList<>();

        PartDraft(String name, int line) {
            this.name = name;
            this.line = line;
        }
    }

    private static final class CubeDraft {
        final int line;
        int[] tex;
        Vec3 pos;
        Vec3 size;
        float inflate;
        boolean mirror;

        CubeDraft(int line) {
            this.line = line;
        }
    }

    private static final class Parser {
        private final String source;
        private final List<String> warnings = new ArrayList<>();
        private final List<PartDraft> parts = new ArrayList<>();
        private final Map<String, PartDraft> byName = new HashMap<>();
        private int textureWidth = 64;
        private int textureHeight = 64;
        private boolean header;
        private PartDraft part;
        private CubeDraft cube;

        Parser(String source) {
            this.source = source;
        }

        ModelReadResult parse(String text) throws ModelFormatException {
            String[] lines = text.replace("\r", "").split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                int no = i + 1;
                String line = stripComment(lines[i]).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!header) {
                    readHeader(line, no);
                } else {
                    statement(line, no);
                }
            }
            if (!header) {
                throw fail(1, "missing 'aimcube:1' header");
            }
            closeCube();
            closePart();
            if (parts.isEmpty()) {
                throw fail(lines.length, "model declares no parts");
            }
            Model model = new Model(buildRoots(), List.of(), List.of(), ModelCapabilities.any(),
                    Map.of(TEXTURE_WIDTH, String.valueOf(textureWidth), TEXTURE_HEIGHT, String.valueOf(textureHeight)),
                    Map.of());
            return new ModelReadResult(Space.mirrorY(model), warnings);
        }

        private void readHeader(String line, int no) throws ModelFormatException {
            if (!line.startsWith(MAGIC)) {
                throw fail(no, "expected 'aimcube:1' header");
            }
            String version = line.substring(MAGIC.length()).trim();
            if (!version.equals("1")) {
                throw fail(no, "unsupported aimcube version '" + version + "'");
            }
            header = true;
        }

        private void statement(String line, int no) throws ModelFormatException {
            Matcher partLine = PART.matcher(line);
            if (partLine.matches()) {
                closeCube();
                closePart();
                openPart(partLine.group(1), no);
                return;
            }
            Matcher key = KEY.matcher(line);
            if (!key.matches()) {
                throw fail(no, "unrecognised line '" + line + "'");
            }
            String name = key.group(1);
            String value = key.group(2).trim();
            switch (name) {
                case "texture_size" -> {
                    float[] size = array(value, 2, no, name);
                    textureWidth = positiveInt(size[0], no, name);
                    textureHeight = positiveInt(size[1], no, name);
                }
                case "cube" -> {
                    closeCube();
                    requirePart(no, name);
                    cube = new CubeDraft(no);
                    applyInlineCubeFields(value, no);
                }
                case "tex", "pos", "size", "inflate", "mirror" -> {
                    if (cube == null) {
                        throw fail(no, "'" + name + "' outside a cube block");
                    }
                    applyCubeField(name, value, no);
                }
                case "parent", "pivot", "rotation", "visible" -> {
                    closeCube();
                    requirePart(no, name);
                    applyPartField(name, value, no);
                }
                default -> warnings.add(source + ":" + no + ": ignored unknown key '" + name + "'");
            }
        }

        private void openPart(String name, int no) throws ModelFormatException {
            if (!NAME.matcher(name).matches()) {
                throw fail(no, "invalid part name '" + name + "'");
            }
            if (byName.containsKey(name)) {
                throw fail(no, "duplicate part name '" + name + "'");
            }
            part = new PartDraft(name, no);
        }

        private void applyPartField(String name, String value, int no) throws ModelFormatException {
            switch (name) {
                case "parent" -> {
                    if (value.equals("root")) {
                        part.parent = null;
                    } else if (byName.containsKey(value)) {
                        part.parent = value;
                    } else {
                        throw fail(no, "unknown parent '" + value + "'; parents must be declared earlier");
                    }
                }
                case "pivot" -> part.pivot = vec(value, no, name);
                case "rotation" -> part.rotation = vec(value, no, name);
                case "visible" -> part.visible = bool(value, no, name);
                default -> throw new IllegalStateException(name);
            }
        }

        private void applyInlineCubeFields(String value, int no) throws ModelFormatException {
            Matcher pair = PAIR.matcher(value);
            int end = 0;
            while (pair.find()) {
                if (!value.substring(end, pair.start()).isBlank()) {
                    throw fail(no, "malformed cube fields '" + value + "'");
                }
                applyCubeField(pair.group(1), pair.group(2), no);
                end = pair.end();
            }
            if (!value.substring(end).isBlank()) {
                throw fail(no, "malformed cube fields '" + value + "'");
            }
        }

        private void applyCubeField(String name, String value, int no) throws ModelFormatException {
            switch (name) {
                case "tex" -> {
                    float[] uv = array(value, 2, no, name);
                    cube.tex = new int[] {nonNegativeInt(uv[0], no, name), nonNegativeInt(uv[1], no, name)};
                }
                case "pos" -> cube.pos = vec(value, no, name);
                case "size" -> cube.size = vec(value, no, name);
                case "inflate" -> cube.inflate = number(value, no, name);
                case "mirror" -> cube.mirror = bool(value, no, name);
                default -> warnings.add(source + ":" + no + ": ignored unknown cube field '" + name + "'");
            }
        }

        private void requirePart(int no, String keyword) throws ModelFormatException {
            if (part == null) {
                throw fail(no, "'" + keyword + "' outside a part");
            }
        }

        private void closeCube() throws ModelFormatException {
            if (cube == null) {
                return;
            }
            if (cube.tex == null) {
                throw fail(cube.line, "cube missing required 'tex'");
            }
            if (cube.pos == null) {
                throw fail(cube.line, "cube missing required 'pos'");
            }
            if (cube.size == null) {
                throw fail(cube.line, "cube missing required 'size'");
            }
            if (cube.size.x() < 0f || cube.size.y() < 0f || cube.size.z() < 0f) {
                throw fail(cube.line, "cube size must not be negative");
            }
            part.cubes.add(cube);
            cube = null;
        }

        private void closePart() throws ModelFormatException {
            if (part == null) {
                return;
            }
            if (part.pivot == null) {
                throw fail(part.line, "part '" + part.name + "' missing required 'pivot'");
            }
            byName.put(part.name, part);
            parts.add(part);
            part = null;
        }

        private List<Node> buildRoots() {
            Map<String, List<Node>> childrenOf = new HashMap<>();
            List<Node> roots = new ArrayList<>();
            for (int i = parts.size() - 1; i >= 0; i--) {
                PartDraft p = parts.get(i);
                List<Node> children = childrenOf.remove(p.name);
                if (children == null) {
                    children = new ArrayList<>();
                }
                Node node = buildNode(p, children);
                List<Node> target = p.parent == null
                        ? roots
                        : childrenOf.computeIfAbsent(p.parent, k -> new ArrayList<>());
                target.add(0, node);
            }
            return roots;
        }

        private static Node buildNode(PartDraft p, List<Node> childParts) {
            List<Node> children = new ArrayList<>();
            Geometry geometry = null;
            if (p.cubes.size() == 1) {
                geometry = cuboid(p.cubes.get(0));
            } else {
                for (int i = 0; i < p.cubes.size(); i++) {
                    Cuboid cuboid = cuboid(p.cubes.get(i));
                    children.add(new Node(p.name + "/cube" + i, Transform.IDENTITY, List.of(), cuboid, true, Map.of()));
                }
            }
            children.addAll(childParts);
            Transform transform = new Transform(p.pivot, quatFromEuler(p.rotation), Vec3.ONE);
            return new Node(p.name, transform, children, geometry, p.visible, Map.of());
        }

        private static Cuboid cuboid(CubeDraft c) {
            return new Cuboid(c.pos, c.size, c.inflate, new CuboidUv.Box(c.tex[0], c.tex[1]), c.mirror);
        }

        private float[] array(String value, int expected, int no, String key) throws ModelFormatException {
            String compact = value.replaceAll("\\s+", "");
            if (compact.length() < 2 || compact.charAt(0) != '[' || compact.charAt(compact.length() - 1) != ']') {
                throw fail(no, "malformed array for '" + key + "': " + value);
            }
            String[] tokens = compact.substring(1, compact.length() - 1).split(",", -1);
            if (tokens.length != expected) {
                throw fail(no, "'" + key + "' needs " + expected + " values: " + value);
            }
            float[] out = new float[expected];
            for (int i = 0; i < expected; i++) {
                out[i] = number(tokens[i], no, key);
            }
            return out;
        }

        private Vec3 vec(String value, int no, String key) throws ModelFormatException {
            float[] a = array(value, 3, no, key);
            return new Vec3(a[0], a[1], a[2]);
        }

        private float number(String text, int no, String key) throws ModelFormatException {
            if (!NUMBER.matcher(text).matches()) {
                throw fail(no, "invalid number for '" + key + "': " + text);
            }
            float v = Float.parseFloat(text);
            if (!Float.isFinite(v)) {
                throw fail(no, "number out of range for '" + key + "': " + text);
            }
            return v;
        }

        private int nonNegativeInt(float v, int no, String key) throws ModelFormatException {
            if (v < 0f || v != Math.rint(v) || v > Integer.MAX_VALUE) {
                throw fail(no, "'" + key + "' must contain non-negative integers");
            }
            return (int) v;
        }

        private int positiveInt(float v, int no, String key) throws ModelFormatException {
            int i = nonNegativeInt(v, no, key);
            if (i == 0) {
                throw fail(no, "'" + key + "' must be positive");
            }
            return i;
        }

        private boolean bool(String value, int no, String key) throws ModelFormatException {
            return switch (value) {
                case "true" -> true;
                case "false" -> false;
                default -> throw fail(no, "'" + key + "' must be true or false: " + value);
            };
        }

        private ModelFormatException fail(int no, String message) {
            return new ModelFormatException(source + ":" + no + ": " + message);
        }

        private static String stripComment(String raw) {
            int hash = raw.indexOf('#');
            return hash < 0 ? raw : raw.substring(0, hash);
        }
    }
}
