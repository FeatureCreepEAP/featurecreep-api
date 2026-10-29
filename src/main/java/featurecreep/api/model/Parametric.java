package featurecreep.api.model;

/** Geometry defined by a procedure that produces a mesh at a requested chordal tolerance. */
public non-sealed interface Parametric extends Geometry {

    Mesh tessellate(double tolerance);
}
