package org.antlr.works.debugger.tree;

import edu.usfca.xj.foundation.notification.XJNotificationCenter;
import edu.usfca.xj.foundation.notification.XJNotificationObserver;
import org.antlr.runtime.Token;
import org.antlr.tool.Grammar;
import org.antlr.works.awtree.AWTreeNode;
import org.antlr.works.debugger.Debugger;
import org.antlr.works.prefs.AWPrefs;
import org.antlr.works.prefs.AWPrefsDialog;

import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.*;
import java.util.List;
/*

[The "BSD licence"]
Copyright (c) 2005-2006 Jean Bovet
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

public class DBParseTreeModel implements XJNotificationObserver {

    public Stack rules = new Stack();
    public Stack backtrackStack = new Stack();

    public Color lookaheadTokenColor;
    public TreeNode lastNode;

    public Debugger debugger;

    public List listeners = new ArrayList();

    public DBParseTreeModel(Debugger debugger) {
        this.debugger = debugger;
        initRules();
        XJNotificationCenter.defaultCenter().addObserver(this, AWPrefsDialog.NOTIF_PREFS_APPLIED);
    }

    public void addListener(DBParseTreeModelListener listener) {
        listeners.add(listener);
    }

    public void fireDataChanged() {
        for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
            DBParseTreeModelListener listener = (DBParseTreeModelListener) iterator.next();
            listener.modelChanged(this);
        }
    }

    public void initRules() {
        rules.clear();
        rules.push(new ParseTreeNode("root"));
    }

    public void clear() {
        initRules();
        backtrackStack.clear();

        setLastNode(null);
        fireDataChanged();
    }

    public void setLastNode(TreeNode node) {
        this.lastNode = node;
    }

    public TreeNode getLastNode() {
        return lastNode;
    }

    public void pushRule(String name, int line, int pos) {
        ParseTreeNode parentRuleNode = (ParseTreeNode)rules.peek();

        ParseTreeNode ruleNode = new ParseTreeNode(name);
        ruleNode.setPosition(line, pos);
        rules.push(ruleNode);

        parentRuleNode.add(ruleNode);

        addNodeToCurrentBacktrack(ruleNode);

        setLastNode(ruleNode);
    }

    public void popRule() {
        rules.pop();
    }

    public TreeNode getRootRule() {
        return (TreeNode)rules.firstElement();
    }

    public TreeNode peekRule() {
        if(rules.isEmpty())
            return null;
        else
            return (TreeNode)rules.peek();
    }

    public void addToken(Token token) {
        ParseTreeNode ruleNode = (ParseTreeNode)rules.peek();
        ParseTreeNode elementNode = new ParseTreeNode(token, debugger.getGrammar().getANTLRGrammar());
        ruleNode.add(elementNode);
        addNodeToCurrentBacktrack(elementNode);
        setLastNode(elementNode);
    }

    public void addException(Exception e) {
        ParseTreeNode ruleNode = (ParseTreeNode)rules.peek();
        ParseTreeNode errorNode = new ParseTreeNode(e);
        ruleNode.add(errorNode);
        addNodeToCurrentBacktrack(errorNode);
        setLastNode(errorNode);
    }

    public void addNodeToCurrentBacktrack(ParseTreeNode node) {
        if(backtrackStack.isEmpty())
            return;

        Backtrack b = (Backtrack) backtrackStack.peek();
        b.addNode(node);
    }

    public void beginBacktrack(int level) {
        backtrackStack.push(new Backtrack(level));
    }

    public void endBacktrack(int level, boolean success) {
        Backtrack b = (Backtrack) backtrackStack.pop();
        b.end(success);
        setLastNode(b.getLastNode());
    }

    public void notificationFire(Object source, String name) {
        if(name.equals(AWPrefsDialog.NOTIF_PREFS_APPLIED)) {
            lookaheadTokenColor = AWPrefs.getLookaheadTokenColor();
        }
    }

    public class ParseTreeNode extends DBTreeNode {
        protected String s;
        protected Exception e;

        public ParseTreeNode(String s) {
            this.s = s;
        }

        public ParseTreeNode(Exception e) {
            this.e = e;
        }

        public ParseTreeNode(Token token, Grammar grammar) {
            super(token, grammar);
        }

        public String toString() {
            if(s != null)
                return s;
            else if(e != null)
                return e.toString();
            else
                return super.toString();
        }

    }

    public class Backtrack {

        public int level;
        public LinkedList nodes = new LinkedList();

        public Backtrack(int level) {
            this.level = level;
        }

        /** Node added to the backtrack object are displayed in blue
         * by default. The definitive color will be applied by the end()
         * method.
         */

        public void addNode(DBTreeNode node) {
            node.setColor(lookaheadTokenColor);
            nodes.add(node);
        }

        public void end(boolean success) {
            Color color = getColor(success);
            for (int i = 0; i < nodes.size(); i++) {
                DBTreeNode node = (DBTreeNode) nodes.get(i);
                node.setColor(color);
            }
        }

        public AWTreeNode getLastNode() {
            if(nodes.isEmpty())
                return null;
            else
                return (AWTreeNode) nodes.getLast();
        }

        protected Color getColor(boolean success) {
            Color c = success?Color.green:Color.red;
            for(int i=1; i<level; i++) {
                c = c.darker().darker();
            }
            return c;
        }

    }

}