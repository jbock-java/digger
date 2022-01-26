/*
 * Copyright (C) 2018 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.internal.codegen.extension.DaggerGraphs.shortestPath;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.DependencyRequestFormatter;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Node;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Reports errors for dependency cycles. */
final class DependencyCycleValidator implements BindingGraphPlugin {

  private final DependencyRequestFormatter dependencyRequestFormatter;

  @Inject
  DependencyCycleValidator(DependencyRequestFormatter dependencyRequestFormatter) {
    this.dependencyRequestFormatter = dependencyRequestFormatter;
  }

  @Override
  public String pluginName() {
    return "Dagger/DependencyCycle";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    ImmutableNetwork<Node, DependencyEdge> dependencyGraph =
        nonCycleBreakingDependencyGraph(bindingGraph);
    // First check the graph for a cycle. If there is one, then we'll do more work to report where.
    if (!Graphs.hasCycle(dependencyGraph)) {
      return;
    }
    // Check each endpoint pair only once, no matter how many parallel edges connect them.
    Set<EndpointPair<Node>> dependencyEndpointPairs = dependencyGraph.asGraph().edges();
    Set<EndpointPair<Node>> visited = new HashSet<>((int) (1.5 * dependencyEndpointPairs.size()));
    for (EndpointPair<Node> endpointPair : dependencyEndpointPairs) {
      cycleContainingEndpointPair(endpointPair, dependencyGraph, visited)
          .ifPresent(cycle -> reportCycle(cycle, bindingGraph, diagnosticReporter));
    }
  }

  private Optional<Cycle<Node>> cycleContainingEndpointPair(
      EndpointPair<Node> endpoints,
      ImmutableNetwork<Node, DependencyEdge> dependencyGraph,
      Set<EndpointPair<Node>> visited) {
    if (!visited.add(endpoints)) {
      // don't recheck endpoints we already know are part of a cycle
      return Optional.empty();
    }

    // If there's a path from the target back to the source, there's a cycle.
    List<Node> cycleNodes =
        shortestPath(dependencyGraph, endpoints.target(), endpoints.source());
    if (cycleNodes.isEmpty()) {
      return Optional.empty();
    }

    Cycle<Node> cycle = Cycle.fromPath(cycleNodes);
    visited.addAll(cycle.endpointPairs()); // no need to check any edge in this cycle again
    return Optional.of(cycle);
  }

  /**
   * Reports a dependency cycle at the dependency into the cycle that is closest to an entry point.
   *
   * <p>For cycles found in reachable binding graphs, looks for the shortest path from the component
   * that contains the cycle (all bindings in a cycle must be in the same component; see below) to
   * some binding in the cycle. Then looks for the last dependency in that path that is not in the
   * cycle; that is the dependency that will be reported, so that the dependency trace will end just
   * before the cycle.
   *
   * <p>For cycles found during full binding graph validation, just reports the component that
   * contains the cycle.
   *
   * <p>Proof (by counterexample) that all bindings in a cycle must be in the same component: Assume
   * one binding in the cycle is in a parent component. Bindings cannot depend on bindings in child
   * components, so that binding cannot depend on the next binding in the cycle.
   */
  private void reportCycle(
      Cycle<Node> cycle, BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (bindingGraph.isFullBindingGraph()) {
      diagnosticReporter.reportComponent(
          ERROR,
          bindingGraph.componentNode(cycle.nodes().iterator().next().componentPath()).orElseThrow(),
          errorMessage(cycle, bindingGraph));
      return;
    }

    List<Node> path = shortestPathToCycleFromAnEntryPoint(cycle, bindingGraph);
    Node cycleStartNode = path.get(path.size() - 1);
    Node previousNode = path.get(path.size() - 2);
    DependencyEdge dependencyToReport =
        chooseDependencyEdgeConnecting(previousNode, cycleStartNode, bindingGraph);
    diagnosticReporter.reportDependency(
        ERROR,
        dependencyToReport,
        errorMessage(cycle.shift(cycleStartNode), bindingGraph)
            // The actual dependency trace is included from the reportDependency call.
            + "\n\nThe cycle is requested via:");
  }

  private List<Node> shortestPathToCycleFromAnEntryPoint(
      Cycle<Node> cycle, BindingGraph bindingGraph) {
    Node someCycleNode = cycle.nodes().iterator().next();
    ComponentNode componentContainingCycle =
        bindingGraph.componentNode(someCycleNode.componentPath()).orElseThrow();
    List<Node> pathToCycle =
        shortestPath(bindingGraph.network(), componentContainingCycle, someCycleNode);
    return subpathToCycle(pathToCycle, cycle);
  }

  /**
   * Returns the subpath from the head of {@code path} to the first node in {@code path} that's in
   * the cycle.
   */
  private List<Node> subpathToCycle(List<Node> path, Cycle<Node> cycle) {
    List<Node> subpath = new ArrayList<>();
    for (Node node : path) {
      subpath.add(node);
      if (cycle.nodes().contains(node)) {
        return subpath;
      }
    }
    throw new IllegalArgumentException(
        "path " + path + " doesn't contain any nodes in cycle " + cycle);
  }

  private String errorMessage(Cycle<Node> cycle, BindingGraph graph) {
    StringBuilder message = new StringBuilder("Found a dependency cycle:");
    List<DependencyRequest> cycleRequests =
        cycle.endpointPairs().stream()
            // TODO(dpb): Would be nice to take the dependency graph here.
            .map(endpointPair -> nonCycleBreakingEdge(endpointPair, graph))
            .map(DependencyEdge::dependencyRequest)
            .collect(toImmutableList());
    ArrayList<DependencyRequest> reversed = new ArrayList<>(cycleRequests);
    Collections.reverse(reversed);
    dependencyRequestFormatter.formatIndentedList(message, reversed, 0);
    message.append("\n")
        .append(dependencyRequestFormatter.format(reversed.get(0)))
        .append("\n")
        .append(Formatter.INDENT).append("...");
    return message.toString();
  }

  /**
   * Returns one of the edges between two nodes that doesn't {@linkplain
   * #breaksCycle(DependencyEdge) break} a cycle.
   */
  private DependencyEdge nonCycleBreakingEdge(EndpointPair<Node> endpointPair, BindingGraph graph) {
    return graph.network().edgesConnecting(endpointPair.source(), endpointPair.target()).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .filter(edge -> !breaksCycle(edge))
        .findFirst()
        .orElseThrow();
  }

  private boolean breaksCycle(DependencyEdge edge) {
    return breaksCycle(edge.dependencyRequest().kind());
  }

  private boolean breaksCycle(RequestKind requestKind) {
    switch (requestKind) {
      case PROVIDER:
      case LAZY:
      case PROVIDER_OF_LAZY:
        return true;

      case INSTANCE:
        // fall through

      default:
        return false;
    }
  }

  private DependencyEdge chooseDependencyEdgeConnecting(
      Node source, Node target, BindingGraph bindingGraph) {
    return bindingGraph.network().edgesConnecting(source, target).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the subgraph containing only {@link DependencyEdge}s that would not break a cycle. */
  // TODO(dpb): Return a network containing only Binding nodes.
  private ImmutableNetwork<Node, DependencyEdge> nonCycleBreakingDependencyGraph(
      BindingGraph bindingGraph) {
    MutableNetwork<Node, DependencyEdge> dependencyNetwork =
        NetworkBuilder.from(bindingGraph.network())
            .expectedNodeCount(bindingGraph.network().nodes().size())
            .expectedEdgeCount(bindingGraph.dependencyEdges().size())
            .build();
    bindingGraph.dependencyEdges().stream()
        .filter(edge -> !breaksCycle(edge))
        .forEach(
            edge -> {
              EndpointPair<Node> endpoints = bindingGraph.network().incidentNodes(edge);
              dependencyNetwork.addEdge(endpoints.source(), endpoints.target(), edge);
            });
    return ImmutableNetwork.copyOf(dependencyNetwork);
  }

  /**
   * An ordered set of endpoint pairs representing the edges in the cycle. The target of each pair
   * is the source of the next pair. The target of the last pair is the source of the first pair.
   */
  static final class Cycle<N> {
    private final Set<EndpointPair<N>> endpointPairs;

    Cycle(Set<EndpointPair<N>> endpointPairs) {
      this.endpointPairs = endpointPairs;
    }

    /**
     * The ordered set of endpoint pairs representing the edges in the cycle. The target of each
     * pair is the source of the next pair. The target of the last pair is the source of the first
     * pair.
     */
    Set<EndpointPair<N>> endpointPairs() {
      return endpointPairs;
    }

    /** Returns the nodes that participate in the cycle. */
    Set<N> nodes() {
      return endpointPairs().stream()
          .flatMap(pair -> Stream.of(pair.source(), pair.target()))
          .collect(toImmutableSet());
    }

    /** Returns the number of edges in the cycle. */
    int size() {
      return endpointPairs().size();
    }

    /**
     * Shifts this cycle so that it starts with a specific node.
     *
     * @return a cycle equivalent to this one but whose first pair starts with {@code startNode}
     */
    Cycle<N> shift(N startNode) {
      List<EndpointPair<N>> endpointPairs = List.copyOf(endpointPairs());
      int startIndex = -1;
      for (int i = 0; i < endpointPairs.size(); i++) {
        EndpointPair<N> pair = endpointPairs.get(i);
        if (pair.source().equals(startNode)) {
          startIndex = i;
          break;
        }
      }
      Preconditions.checkArgument(
          startIndex >= 0, "startNode (%s) is not part of this cycle: %s", startNode, this);
      if (startIndex == 0) {
        return this;
      }
      Set<EndpointPair<N>> shifted = new LinkedHashSet<>();
      shifted.addAll(endpointPairs.subList(startIndex, this.endpointPairs.size()));
      shifted.addAll(endpointPairs.subList(0, size() - startIndex));
      return new Cycle<>(shifted);
    }

    @Override
    public String toString() {
      return endpointPairs().toString();
    }

    /**
     * Creates a {@link Cycle} from a nonempty list of nodes, assuming there is an edge between each
     * pair of nodes as well as an edge from the last node to the first.
     */
    static <N> Cycle<N> fromPath(List<N> nodes) {
      Preconditions.checkArgument(!nodes.isEmpty());
      Set<EndpointPair<N>> cycle = new LinkedHashSet<>();
      cycle.add(EndpointPair.ordered(nodes.get(nodes.size() - 1), nodes.get(0)));
      for (int i = 0; i < nodes.size() - 1; i++) {
        cycle.add(EndpointPair.ordered(nodes.get(i), nodes.get(i + 1)));
      }
      return new Cycle<>(cycle);
    }
  }
}
