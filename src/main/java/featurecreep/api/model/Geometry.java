package featurecreep.api.model;

/** Geometry attached to a node. Parametric geometry is open for implementations outside this package. */
public sealed interface Geometry permits Cuboid, Mesh, Parametric {
}
