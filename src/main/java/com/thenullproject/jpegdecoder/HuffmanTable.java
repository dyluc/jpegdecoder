package com.thenullproject.jpegdecoder;

import java.util.HashMap;

class HuffmanTable {

    private final HashMap<Integer, int[]> lookup;
    private final Node root;

    private static class Node { // node in binary tree

        private int symbol;
        private Node[] children; // children[0] - left child, children[1] right child
        private Node parent;

        private Node() { // root
            symbol = -1; // nodes left with symbol -1 are not leaf nodes, i.e have children
        }
        private Node(Node parent) {
            this();
            this.parent = parent;
        }
        private void initChildNodes() {
            children = new Node[]{new Node(this), new Node(this)};
        }
    }

    HuffmanTable(HashMap<Integer, int[]> lookup) {

        // hashmap reference to code lengths with corresponding symbols
        this.lookup = lookup;

        // construct huffman tree
        root = new Node();
        root.initChildNodes();
        Node leftMost = root.children[0];
        Node current;

        for(int i = 1; i <= lookup.size(); i++) {
            if(getSymbolCount(i) == 0) {
                current = leftMost;
                while(current != null) {
                    current.initChildNodes();
                    current = getRightNodeOf(current);
                }
                leftMost = leftMost.children[0];
            } else { // symbols to put into the nodes of the binary tree
                for(int symbol : getSymbols(i)) {
                    leftMost.symbol = symbol;
                    leftMost = getRightNodeOf(leftMost);
                }
                leftMost.initChildNodes();
                current = getRightNodeOf(leftMost);
                leftMost = leftMost.children[0];
                while(current != null) {
                    current.initChildNodes();
                    current = getRightNodeOf(current);
                }
            }
        }

    }

    private int getSymbolCount(int n) { // # of symbols with length n bits
        return lookup.get(n).length;
    }
    private int[] getSymbols(int n) { // returns list of symbols with length n bits
        return lookup.get(n);
    }
    private Node getRightNodeOf(Node node) {
        if(node.parent.children[0] == node) return node.parent.children[1];
        int traverseCount = 0;

        while (node.parent != null && node.parent.children[1] == node) {
            node = node.parent;
            traverseCount++;
        }

        if(node.parent == null) return null;

        node = node.parent.children[1];

        while (traverseCount > 0) {
            node = node.children[0];
            traverseCount--;
        }

        return node;
    }

    public int getCode(BitStream stream) {
        Node currentNode = root;
        while(currentNode.symbol == -1) {
            int bit = stream.bit();
            if(bit < 0) { // end of bit stream
                return bit; // no more codes to read
            }
            currentNode = currentNode.children[bit];
        }
        return currentNode.symbol;
    }
}
