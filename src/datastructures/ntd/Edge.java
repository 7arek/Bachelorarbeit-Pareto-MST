package datastructures.ntd;

/** <li>Diese Klasse wird aus Effizienz-gründen nur an wenigen Stellen benutzt und dient eher als Tupel</li>
 * <li>Nach initialisierung ist u < v sichergestellt</li>
 */
public class Edge{
    private int u;
    private int v;

    public Edge(int u, int v) {
        if (u < v) {
            this.u = u;
            this.v = v;
        } else {
            this.u = v;
            this.v = u;
        }
    }

    public int getU() {
        return u;
    }

    public int getV() {
        return v;
    }
}