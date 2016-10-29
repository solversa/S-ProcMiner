package org.ag.processmining.sna;

import com.google.common.collect.ImmutableList;
import org.ag.processmining.log.model.Originator;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Created by ahmed.gater on 29/10/2016.
 */

public class SocialNetwork {
    DefaultDirectedWeightedGraph<Originator, DefaultWeightedEdge> sGraph;

    public SocialNetwork() {
        this.sGraph = new DefaultDirectedWeightedGraph(DefaultWeightedEdge.class);
    }


    public void addRelation(Originator src, Originator dest, double weight) {
        double nWeight = weight;
        if (!this.sGraph.containsEdge(src, dest)) {
            Graphs.addEdgeWithVertices(this.sGraph, src, dest);
            nWeight = weight;
        } else {
            weight = this.sGraph.getEdgeWeight(this.sGraph.getEdge(src, dest)) + weight;
            nWeight = weight + this.sGraph.getEdgeWeight(this.sGraph.getEdge(src, dest));
        }
        this.sGraph.setEdgeWeight(this.sGraph.getEdge(src, dest), nWeight);
    }

    public void addRelation(Originator src, Originator dest) {
        addRelation(src, dest, 1);
    }

    public SocialNetwork merge(SocialNetwork sn1) {
        SocialNetwork sn = new SocialNetwork();
        new ImmutableList.Builder<DefaultWeightedEdge>()
                .addAll(sn1.sGraph.edgeSet())
                .addAll(this.sGraph.edgeSet())
                .build().stream().forEach(e -> sn.addRelation(sGraph.getEdgeSource(e),
                sGraph.getEdgeTarget(e),
                sGraph.getEdgeWeight(e)));
        return sn;
    }
}