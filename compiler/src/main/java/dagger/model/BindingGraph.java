/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.model;

import static com.google.common.graph.Graphs.inducedSubgraph;
import static com.google.common.graph.Graphs.reachableNodes;
import static com.google.common.graph.Graphs.transpose;
import static dagger.internal.codegen.base.Util.intersection;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.Module;
import dagger.internal.codegen.base.Suppliers;
import dagger.spi.model.Key;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A graph of bindings, dependency requests, and components.
 *
 * <p>A {@link BindingGraph} represents one of the following:
 *
 * <ul>
 *   <li>an entire component hierarchy rooted at a {@link dagger.Component}
 *   <li>a partial component hierarchy rooted at a {@link dagger.Subcomponent}
 *       (only when the value of {@code -Adagger.fullBindingGraphValidation}
 *       is not {@code NONE})
 *   <li>the bindings installed by a {@link Module},
 *       including all subcomponents generated by {@link Module#subcomponents()}
 * </ul>
 *
 * <p>In the case of a {@link BindingGraph} representing a module, the root {@link ComponentNode} will
 * actually represent the module type. The graph will also be a {@linkplain #isFullBindingGraph()
 * full binding graph}, which means it will contain all bindings in all modules, as well as nodes
 * for their dependencies. Otherwise it will contain only bindings that are reachable from at least
 * one {@linkplain #entryPointEdges() entry point}.
 *
 * <h2>Nodes</h2>
 *
 * <p>There is a <b>{@link Binding}</b> for each owned binding in the graph. If a binding is owned
 * by more than one component, there is one binding object for that binding for every owning
 * component.
 *
 * <p>There is a <b>{@linkplain ComponentNode component node}</b> (without a binding) for each
 * component in the graph.
 *
 * <h3>Edges</h3>
 *
 * <p>There is a <b>{@linkplain DependencyEdge dependency edge}</b> for each dependency request in
 * the graph. Its target node is the binding for the binding that satisfies the request. For entry
 * point dependency requests, the source node is the component node for the component for which it
 * is an entry point. For other dependency requests, the source node is the binding for the binding
 * that contains the request.
 *
 * <p>There is a <b>subcomponent edge</b> for each parent-child component relationship in the graph.
 * The target node is the component node for the child component. For subcomponents defined by a
 * {@linkplain SubcomponentCreatorBindingEdge subcomponent creator binding} (either a method on the
 * component or a set of {@code @Module.subcomponents} annotation values), the source node is the
 * binding for the {@code @Subcomponent.Builder} type. For subcomponents defined by {@linkplain
 * ChildFactoryMethodEdge subcomponent factory methods}, the source node is the component node for
 * the parent.
 *
 * <p><b>Note that this API is experimental and will change.</b>
 */
public abstract class BindingGraph {
  private final ImmutableNetwork<Node, Edge> network;
  private final Set<Binding> bindings;
  private final Set<MissingBinding> missingBindings;
  private final Set<ComponentNode> componentNodes;

  private final Supplier<ImmutableNetwork<Node, DependencyEdge>> dependencyGraph = Suppliers.memoize(() -> {
    MutableNetwork<Node, DependencyEdge> dependencyGraph =
        NetworkBuilder.from(network())
            .expectedNodeCount(network().nodes().size())
            .expectedEdgeCount((int) dependencyEdgeStream().count())
            .build();
    network().nodes().forEach(dependencyGraph::addNode); // include disconnected nodes
    dependencyEdgeStream()
        .forEach(
            edge -> {
              EndpointPair<Node> endpoints = network().incidentNodes(edge);
              dependencyGraph.addEdge(endpoints.source(), endpoints.target(), edge);
            });
    return ImmutableNetwork.copyOf(dependencyGraph);
  });

  protected BindingGraph(
      ImmutableNetwork<Node, Edge> network,
      Set<Binding> bindings,
      Set<MissingBinding> missingBindings,
      Set<ComponentNode> componentNodes) {
    this.network = requireNonNull(network);
    this.bindings = requireNonNull(bindings);
    this.missingBindings = requireNonNull(missingBindings);
    this.componentNodes = requireNonNull(componentNodes);
  }

  /** Returns the graph in its {@link Network} representation. */
  public final ImmutableNetwork<Node, Edge> network() {
    return network;
  }

  @Override
  public String toString() {
    return network().toString();
  }

  /**
   * Returns {@code true} if this graph was constructed from a module for full binding graph
   * validation.
   *
   * @deprecated use {@link #isFullBindingGraph()} to tell if this is a full binding graph, or
   *     {@link ComponentNode#isRealComponent() rootComponentNode().isRealComponent()} to tell if
   *     the root component node is really a component or derived from a module. Dagger can generate
   *     full binding graphs for components and subcomponents as well as modules.
   */
  @Deprecated
  public boolean isModuleBindingGraph() {
    return !rootComponentNode().isRealComponent();
  }

  /**
   * Returns {@code true} if this is a full binding graph, which contains all bindings installed in
   * the component, or {@code false} if it is a reachable binding graph, which contains only
   * bindings that are reachable from at least one {@linkplain #entryPointEdges() entry point}.
   *
   * @see <a href="https://dagger.dev/compiler-options#full-binding-graph-validation">Full binding
   *     graph validation</a>
   */
  public abstract boolean isFullBindingGraph();

  /**
   * Returns {@code true} if the {@link #rootComponentNode()} is a subcomponent. This occurs in
   * when {@code -Adagger.fullBindingGraphValidation} is used in a compilation with a subcomponent.
   *
   * @deprecated use {@link ComponentNode#isSubcomponent() rootComponentNode().isSubcomponent()}
   *     instead
   */
  @Deprecated
  public boolean isPartialBindingGraph() {
    return rootComponentNode().isSubcomponent();
  }

  /** Returns the bindings. */
  public Set<Binding> bindings() {
    return bindings;
  }

  /** Returns the bindings for a key. */
  public Set<Binding> bindings(Key key) {
    return bindings.stream()
        .filter(binding -> binding.key().equals(key))
        .collect(toImmutableSet());
  }

  /** Returns the nodes that represent missing bindings. */
  public Set<MissingBinding> missingBindings() {
    return missingBindings;
  }

  /** Returns the component nodes. */
  public Set<ComponentNode> componentNodes() {
    return componentNodes;
  }

  /** Returns the component node for a component. */
  public Optional<ComponentNode> componentNode(ComponentPath component) {
    return componentNodes().stream()
        .filter(node -> node.componentPath().equals(component))
        .findFirst();
  }

  /** Returns the component node for the root component. */
  public ComponentNode rootComponentNode() {
    return componentNodes().stream()
        .filter(node -> node.componentPath().atRoot())
        .findFirst()
        .orElseThrow();
  }

  /** Returns the dependency edges. */
  public Set<DependencyEdge> dependencyEdges() {
    return dependencyEdgeStream().collect(toImmutableSet());
  }

  /**
   * Returns the dependency edges for all entry points for all components and subcomponents. Each
   * edge's source node is a component node.
   */
  public Set<DependencyEdge> entryPointEdges() {
    return entryPointEdgeStream().collect(toImmutableSet());
  }

  /**
   * Returns the edges for entry points that transitively depend on a binding or missing binding for
   * a key.
   */
  public Set<DependencyEdge> entryPointEdgesDependingOnBinding(
      MaybeBinding binding) {
    ImmutableNetwork<Node, DependencyEdge> dependencyGraph = dependencyGraph();
    Network<Node, DependencyEdge> subgraphDependingOnBinding =
        inducedSubgraph(
            dependencyGraph, reachableNodes(transpose(dependencyGraph).asGraph(), binding));
    return intersection(entryPointEdges(), subgraphDependingOnBinding.edges());
  }

  /**
   * Returns the bindings that a given binding directly requests as a dependency. Does not include
   * any {@link MissingBinding}s.
   */
  public Set<Binding> requestedBindings(Binding binding) {
    return network().successors(binding).stream()
        .flatMap(instancesOf(Binding.class))
        .collect(toImmutableSet());
  }

  /** Returns a subnetwork that contains all nodes but only {@link DependencyEdge}s. */
  private ImmutableNetwork<Node, DependencyEdge> dependencyGraph() {
    return dependencyGraph.get();
  }

  private Stream<DependencyEdge> dependencyEdgeStream() {
    return network().edges().stream().flatMap(instancesOf(DependencyEdge.class));
  }

  private Stream<DependencyEdge> entryPointEdgeStream() {
    return dependencyEdgeStream().filter(DependencyEdge::isEntryPoint);
  }

  /**
   * An edge in the binding graph. Either a {@link DependencyEdge}, a {@link
   * ChildFactoryMethodEdge}, or a {@link SubcomponentCreatorBindingEdge}.
   */
  public interface Edge {
  }

  /**
   * An edge that represents a dependency on a binding.
   *
   * <p>Because one {@link DependencyRequest} may represent a dependency from two bindings (e.g., a
   * dependency of {@code Foo<String>} and {@code Foo<Number>} may have the same key and request
   * element), this class does not override {@link #equals(Object)} to use value semantics.
   *
   * <p>For entry points, the source node is the {@link ComponentNode} that contains the entry
   * point. Otherwise the source node is a {@link Binding}.
   *
   * <p>For dependencies on missing bindings, the target node is a {@link MissingBinding}. Otherwise
   * the target node is a {@link Binding}.
   */
  public interface DependencyEdge extends Edge {
    /** The dependency request. */
    DependencyRequest dependencyRequest();

    /** Returns {@code true} if this edge represents an entry point. */
    boolean isEntryPoint();
  }

  /**
   * An edge that represents a subcomponent factory method linking a parent component to a child
   * subcomponent.
   */
  public interface ChildFactoryMethodEdge extends Edge {
    /** The subcomponent factory method element. */
    ExecutableElement factoryMethod();
  }

  /**
   * An edge that represents the link between a parent component and a child subcomponent implied by
   * a subcomponent creator ({@linkplain dagger.Subcomponent.Builder builder} or {@linkplain
   * dagger.Subcomponent.Factory factory}) binding.
   *
   * <p>The {@linkplain com.google.common.graph.EndpointPair#source() source node} of this edge is a
   * {@link Binding} for the subcomponent creator {@link Key} and the {@linkplain
   * com.google.common.graph.EndpointPair#target() target node} is a {@link ComponentNode} for the
   * child subcomponent.
   */
  public interface SubcomponentCreatorBindingEdge extends Edge {
    /**
     * The modules that {@linkplain Module#subcomponents() declare the subcomponent} that generated
     * this edge. Empty if the parent component has a subcomponent creator method and there are no
     * declaring modules.
     */
    Set<TypeElement> declaringModules();
  }

  /** A node in the binding graph. Either a {@link Binding} or a {@link ComponentNode}. */
  // TODO(dpb): Make all the node/edge types top-level.
  public interface Node {
    /** The component this node belongs to. */
    ComponentPath componentPath();
  }

  /** A node in the binding graph that is either a {@link Binding} or a {@link MissingBinding}. */
  public interface MaybeBinding extends Node {

    /** The component that owns the binding, or in which the binding is missing. */
    @Override
    ComponentPath componentPath();

    /** The key of the binding, or for which there is no binding. */
    Key key();

    /** The binding, or empty if missing. */
    Optional<Binding> binding();
  }

  /** A node in the binding graph that represents a missing binding for a key in a component. */
  public abstract static class MissingBinding implements MaybeBinding {
    /** The component in which the binding is missing. */
    @Override
    public abstract ComponentPath componentPath();

    /** The key for which there is no binding. */
    @Override
    public abstract Key key();

    /** @deprecated This always returns {@code Optional.empty()}. */
    @Override
    @Deprecated
    public Optional<Binding> binding() {
      return Optional.empty();
    }

    @Override
    public String toString() {
      return String.format("missing binding for %s in %s", key(), componentPath());
    }
  }

  /**
   * A <b>component node</b> in the graph. Every entry point {@linkplain DependencyEdge dependency
   * edge}'s source node is a component node for the component containing the entry point.
   */
  public interface ComponentNode extends Node {

    /** The component represented by this node. */
    @Override
    ComponentPath componentPath();

    /**
     * Returns {@code true} if the component is a {@code @Subcomponent} or
     * {@code @ProductionSubcomponent}.
     */
    boolean isSubcomponent();

    /**
     * Returns {@code true} if the component is a real component, or {@code false} if it is a
     * fictional component based on a module.
     */
    boolean isRealComponent();

    /** The scopes declared on this component. */
    Set<Scope> scopes();
  }
}
