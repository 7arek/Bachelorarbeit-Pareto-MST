package benchmarking;

import benchmarking.calculators.IntBoxer;

import java.math.BigInteger;

public class Result {
    public String solution;
    public boolean safeSolution;

    public String abortReason;

    public int graph_num_vertices;
    public int graph_num_edges;

    public int ntd_tw;
    public int ntd_num_nodes;
    public int ntd_num_join_nodes;

    //Zeit in ms (max -> 24 Tage)
    public int jd_total_time;
    public IntBoxer jd_td_time;
    public IntBoxer jd_ntd_time;
    public IntBoxer my_ntd_time;
    public IntBoxer better_root_time;
    public IntBoxer pareto_mst_time;

    public BigInteger estimated_time;
    public int root_count;

    public int pareto_mst_max_heap_usage = 0;

    public boolean outsourced = false;
    public int solutionCount = -1;

    public Result(Result result) {
        solution = result.solution;
        safeSolution = result.safeSolution;
        abortReason = result.abortReason;
        graph_num_vertices = result.graph_num_vertices;
        graph_num_edges = result.graph_num_edges;
        ntd_tw = result.ntd_tw;
        ntd_num_nodes = result.ntd_num_nodes;
        ntd_num_join_nodes = result.ntd_num_join_nodes;
        jd_total_time = result.jd_total_time;
        jd_td_time = new IntBoxer(result.jd_td_time);
        jd_ntd_time = new IntBoxer(result.jd_ntd_time);
        my_ntd_time = new IntBoxer(result.my_ntd_time);
        better_root_time = new IntBoxer(result.better_root_time);
        pareto_mst_time = new IntBoxer(result.pareto_mst_time);
        estimated_time = result.estimated_time;
        root_count = result.root_count;
        pareto_mst_max_heap_usage = result.pareto_mst_max_heap_usage;
        outsourced = result.outsourced;
        solutionCount = result.solutionCount;
    }



    public Result() {
        safeSolution = false;
        abortReason = "UNKNOWN";
        jd_td_time = new IntBoxer();
        jd_ntd_time = new IntBoxer();
        my_ntd_time = new IntBoxer();
        better_root_time = new IntBoxer();
        pareto_mst_time = new IntBoxer();
    }
}
