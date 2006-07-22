/*

[The "BSD licence"]
Copyright (c) 2005 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.antlr.works.visualization.graphics.graph;

import org.antlr.analysis.NFAState;
import org.antlr.works.visualization.fa.FAState;
import org.antlr.works.visualization.fa.FATransition;
import org.antlr.works.visualization.graphics.GContext;
import org.antlr.works.visualization.graphics.path.GPath;
import org.antlr.works.visualization.graphics.path.GPathElement;
import org.antlr.works.visualization.graphics.path.GPathGroup;
import org.antlr.works.visualization.graphics.primitive.GDimension;
import org.antlr.works.visualization.graphics.shape.GNode;
import org.antlr.works.visualization.skin.syntaxdiagram.SDSkin;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GGraphGroup extends GGraphAbstract {

    public static final int TITLE_OFFSET = 100;

    public GDimension dimension = new GDimension();
    public List graphs = new ArrayList();
    public GPathGroup pathGroup = new GPathGroup();

    protected GContext defaultContext;

    public GGraphGroup() {
        // The default context is used to evaluate the position of certain objects in addPath()
        // because position requires a context to be evaluated ;-)
        defaultContext = new GContext();
        defaultContext.setSkin(new SDSkin());
    }

    public void setEnable(boolean flag) {
        pathGroup.setEnable(flag);
    }

    public void setContext(GContext context) {
        super.setContext(context);
        for (Iterator iterator = graphs.iterator(); iterator.hasNext();) {
            GGraph graph = (GGraph) iterator.next();
            graph.setContext(context);
        }
        pathGroup.setContext(context);
    }

    public void add(GGraph graph) {
        dimension.maxWidth(graph.dimension.width);
        dimension.addUp(graph.dimension.up);
        dimension.addDown(graph.dimension.down);
        if(graphs.size()>0)
            dimension.addDown(GContext.LINE_SPACE);
        graphs.add(graph);
    }

    public float getHeight() {
        return getDimension().getPixelHeight(context);
    }

    public float getWidth() {
        return getDimension().getPixelWidth(context)+TITLE_OFFSET;
    }

    protected int pathIndex;

    public List getTransitionsMatchingSkippedStates(List candidates, List states) {
        /** First convert the list of NFAStates to a list of Integer containing
         * the state number
         */
        List statesNumbers = new ArrayList();
        for(int i=0; i<states.size(); i++) {
            statesNumbers.add((new Integer(((NFAState)states.get(i)).stateNumber)));
        }

        /** Select only the transitions that containing all the state numbers */
        List newCandidates = new ArrayList();
        for(int c=0; c<candidates.size(); c++) {
            FATransition t = (FATransition)candidates.get(c);
            if(t.skippedStates != null && t.skippedStates.containsAll(statesNumbers)) {
                newCandidates.add(t);
            }
        }

        return newCandidates;
    }

    public FATransition getNodeTransitionToNextNonSkippedState(GNode node, List path) {
        if(node == null)
            return null;

        List candidateTransitions = new ArrayList(node.state.transitions);
        FATransition candidate = null;
        int start = pathIndex;

        loop:
        for(; pathIndex < path.size(); pathIndex++) {
            candidateTransitions = getTransitionsMatchingSkippedStates(candidateTransitions, path.subList(start, pathIndex+1));
            switch(candidateTransitions.size()) {
                case 0: // No more transitions. Exit and use the candidate transition.
                    break loop;

                case 1:
                    // The uniquely identified transition has been found.
                    // Continue to loop until all skipped states have been found.
                    candidate = (FATransition) candidateTransitions.get(0);
                    break;

                default:
                    // More than one transition candidates found. Look if any of these
                    // transitions's target state correspond to the next path state to avoid
                    // missing a transition: this can happen if a transition is a subset of
                    // the others (the next state of the path can return no transition at all)
                    if(pathIndex+1 < path.size()) {
                        NFAState nextPathState = (NFAState) path.get(pathIndex+1);
                        for(int i=0; i<candidateTransitions.size(); i++) {
                            FATransition t = (FATransition)candidateTransitions.get(i);
                            if(t.target.stateNumber == nextPathState.stateNumber) {
                                pathIndex++;    // always points to the next element after the transition
                                return t;
                            }
                        }
                    }
                    break;
            }
        }

        return candidate;
    }

    public void addNextElementInSameRule(List elements, GNode node, GNode nextNode) {
        elements.add(GPathElement.createElement(node));

        // Use nextNode instead of nextState (previously in parameter) because nextState
        // could be null if it was skipped during optimization. nextNode cannot be null.
        FATransition t = node.state.getTransitionToStateNumber(nextNode.state.stateNumber);
        if(t == null) {
            // Probably a loop. In this case, the transition is located in the target state.
            t = nextNode.state.getTransitionToStateNumber(node.state.stateNumber);
            if(t == null) {
                // Still no transition found. This is a reference to the same rule (recursive)
                elements.add(GPathElement.createLink(node, nextNode));
            } else {
                // Add the loop transition to the path
                elements.add(GPathElement.createElement(nextNode.getLink(t)));
            }
        } else {
            // Add the transition to the path
            elements.add(GPathElement.createElement(node.getLink(t)));
        }
    }

    public void addNextElementInOtherRule(List elements, GNode node, GNode externalNode, GNode nextNode, NFAState nextState) {
        if(externalNode == null) {
            // The external node is not specified. Try to find it.
            if(node.state.getFirstTransition() == null) {
                // If the node contains no transition (probably if it is at the end of a rule), then
                // ignore externalNode. We will draw only a link from node to nextNode.
            } else {
                // Find the transition that points to the external rule ref
                FATransition t = node.state.getTransitionToExternalStateRule(nextState.getEnclosingRule());
                if(t == null) {
                    System.err.println("[GGraphGroup] No transition to external state "+nextState.stateNumber+"["+nextState.getEnclosingRule()+"] - using first transition by default");
                    t = node.state.getFirstTransition();
                }
                externalNode = findNodeForStateNumber(t.target.stateNumber);
            }
        }

        if(externalNode == null) {
            // Add the link between node and nextNode, ignore externalNode because it doesn't exist
            elements.add(GPathElement.createElement(node));
            elements.add(GPathElement.createLink(node, nextNode));
        } else {
            elements.add(GPathElement.createElement(node));

            // Add the link between node and externalNode.
            FATransition t = node.state.getTransitionToStateNumber(externalNode.state.stateNumber);
            elements.add(GPathElement.createElement(node.getLink(t)));
            elements.add(GPathElement.createElement(externalNode));

            // Add the link between externalNode and nextNode
            elements.add(GPathElement.createLink(externalNode, nextNode));
        }
    }

    public void addPath(List path, boolean disabled, Map skippedStates) {
        List elements = new ArrayList();

        /** path contains a list of NFAState states (from ANTLR): they represent
         * all the states along the path. The graphical representation of the NFA/SD
         * does not necessarily contains all the states of the path because the representation
         * can be simplified to remove all unecessary states.
         * The problem here is to use the information stored in the transition of the
         * graphical representation to figure out exactly which graphical node corresponds
         * to the path.
         */

        /*System.out.println("***");
        for (Iterator iterator = path.iterator(); iterator.hasNext();) {
            NFAState state = (NFAState)iterator.next();
            System.out.println(state+" - "+state.getEnclosingRule());
        } */

        NFAState state;
        GNode node;
        NFAState nextState = null;
        GNode nextNode = null;
        for(pathIndex = 0; pathIndex < path.size(); pathIndex++) {
            if(pathIndex == 0) {
                nextState = (NFAState)path.get(pathIndex);
                nextNode = findNodeForStateNumber(nextState.stateNumber);
                if(nextNode == null) {
                    // A path can start from anywhere in the graph. It might happen
                    // that the starting state of the path has been skipped by
                    // the optimization in FAFactory. We use the skippedStates mapping
                    // to find out what is the parent state of the skipped state.
                    FAState parentState = (FAState) skippedStates.get(new Integer(nextState.stateNumber));
                    if(parentState == null) {
                        System.err.println("[GGraphGroup] Starting path state "+nextState.stateNumber+"["+nextState.getEnclosingRule()+"] cannot be found in the graph");
                        return;
                    } else {
                        nextNode = findNodeForStateNumber(parentState.stateNumber);
                    }
                }
                continue;
            } else {
                state = nextState;
                node = nextNode;
            }

            nextState = (NFAState)path.get(pathIndex);
            nextNode = findNodeForStateNumber(nextState.stateNumber);

            GNode externalNode = null;

            if(nextNode == null) {
                // The state has probably been skipped during the graphical rendering.
                // Find the next non-skipped state.
                FATransition t = getNodeTransitionToNextNonSkippedState(node, path);
                if(t == null) {
                    // No transition found. Look in the skipped states mapping because
                    // it might be possible that the next state is in another rule but
                    // cannot be found because it has been skipped.

                    FAState parentState = (FAState) skippedStates.get(new Integer(nextState.stateNumber));
                    if(parentState == null) {
                        //  OK. The node really does not exist. Continue by skipping it.
                        nextNode = node;
                        continue;
                    } else {
                        nextNode = findNodeForStateNumber(parentState.stateNumber);
                    }
                } else {
                    // pathIndex can be out of range because getNodeTransitionToNextNonSkippedState()
                    // is incrementing it
                    if(pathIndex >= path.size()) {
                        nextNode = findNodeForStateNumber(t.target.stateNumber);
                    } else {
                        nextState = (NFAState)path.get(pathIndex);

                        if(t.target.stateNumber == nextState.stateNumber) {
                            nextNode = findNodeForStateNumber(t.target.stateNumber);
                        } else {
                            // The only case that the target state of the transition if not
                            // the next state of the path is when the next state of the path
                            // is in another rule. In this case, the target state of the transition
                            // will contain a negative state number indicating an external rule reference:
                            // this external rule reference is added by AW during rendering and is not
                            // part of any ANTLR NFA.

                            // This node is the node representing the external rule reference
                            // before jumping outside of the rule
                            externalNode = findNodeForStateNumber(t.target.stateNumber);

                            // This node is the first node in the other rule
                            nextNode = findNodeForStateNumber(nextState.stateNumber);
                        }
                    }
                }
            }

            if(state == null || node == null || nextNode == null)
                continue;

            if(state.getEnclosingRule().equals(nextState.getEnclosingRule()))
                addNextElementInSameRule(elements, node, nextNode);
            else
                addNextElementInOtherRule(elements, node, externalNode, nextNode, nextState);
        }

        if(nextNode != null)
            elements.add(GPathElement.createElement(nextNode));

        pathGroup.addPath(new GPath(elements, disabled));
    }

    public void addUnreachableAlt(NFAState state, Integer alt) {
        List elements = new ArrayList();

        GNode node = findNodeForStateNumber(state.stateNumber);
        if(node == null) {
            System.err.println("[GGraphGroup] Decision state "+state.stateNumber+"["+state.getEnclosingRule()+"] cannot be found in the graph");
            return;
        }
        List transitions = node.state.transitions;
        int altNum = alt.intValue()-1;

        if(altNum >= transitions.size()) {
            System.err.println("[GGraphGroup] Unreachable alt "+altNum+"["+state.getEnclosingRule()+"] is out of bounds: "+transitions.size());
            return;
        }

        FATransition t = (FATransition) transitions.get(altNum);

        elements.add(GPathElement.createElement(node));
        elements.add(GPathElement.createElement(node.getLink(t)));

        /** This path has to be visible but not selectable */
        GPath path = new GPath(elements, true);
        path.setVisible(true);
        path.setSelectable(false);
        pathGroup.addPath(path);
    }

    public GNode findNodeForStateNumber(int stateNumber) {
        for (Iterator iterator = graphs.iterator(); iterator.hasNext();) {
            GGraph graph = (GGraph) iterator.next();
            GNode node = graph.findNodeForStateNumber(stateNumber);
            if(node != null) {
                return node;
            }
        }
        return null;
    }

    public GDimension getDimension() {
        return dimension;
    }

    public void render(float ox, float oy) {
        ox += TITLE_OFFSET;
        for (int i = 0; i<graphs.size(); i++) {
            GGraph graph = (GGraph)graphs.get(i);
            graph.render(ox, oy);
            if(i<graphs.size()-1)
                oy += graph.getHeight()+context.getPixelLineSpace();
        }

        setRendered(true);
    }

    public void draw() {
        context.nodeColor = Color.black;
        context.linkColor = Color.black;
        context.setLineWidth(1);

        for (int i = 0; i<graphs.size(); i++) {
            GGraph graph = (GGraph)graphs.get(i);
            graph.draw();
            context.setColor(Color.black);
            context.drawString(context.getRuleFont(), graph.name, TITLE_OFFSET-5, graph.offsetY, GContext.ALIGN_RIGHT);
        }

        pathGroup.draw();

        if(context.drawdimension) {
            context.setLineWidth(1);
            context.setColor(Color.lightGray);
            float width = getDimension().getPixelWidth(context);
            float up = getDimension().getPixelUp(context);
            float down = getDimension().getPixelDown(context);
            if(up+down>0)
                context.drawRect(0, 0, width, up+down, false);
        }
    }

}
