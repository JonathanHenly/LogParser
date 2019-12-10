package com.mactracker.main.log;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;


/**
 * The {@code AbbreviationTrieList} class represents a symbol table of key-value
 * pairs with ASCII {@code String} keys mapped to {@code String} values.
 * <p>
 * This class implements a 95-way trie (ASCII characters ranging from decimal
 * values 33 to 126). Thus, prefer short key values to long key values as each
 * non-leaf node will contain a node array of length 95.
 * 
 * @author GroupZ
 */
public class AbbreviationTrie {
    /**
     * Put methods will return this if the passed in key contains ASCII control
     * characters (i.e. ASCII decimal values 0 (null) through 32 (space) or 127
     * (DEL)).
     */
    public static final int KEY_HAS_CTRL_CHARS = -1;
    /**
     * Put methods will return this if the passed in key has already been "put"
     * into this AbbreviationTrieMap.
     */
    public static final int DUPLICATE_KEY = -2;
    /**
     * Put methods will return this if this AbbreviationTrieList is at maximum
     * capacity.
     */
    public static final int TRIE_IS_FULL = -3;
    /**
     * Get methods will return this if the passed in key or value index does not
     * map to a valid abbreviation.
     */
    public static final int VALUE_NOT_FOUND = -4;
    
    /**
     * The maximum number of key-value pairs this AbbreviationTrieMap can store.
     * <p>
     * Equivalent to {@code Integer.MAX_VALUE - 1}.
     */
    public static final int MAX_CAPACITY = Integer.MAX_VALUE - 1;
    
    // ASCII control characters range from 0 (null) to 32 (space)
    private static final int ASCII_CTRL_OFFSET = 32;
    // using 127 instead of 128, ASCII character 127 is DEL
    private static final int ASCII_ALPHABET_LENGTH = 127 - ASCII_CTRL_OFFSET;
    
    /* DEBUG */
    private FileWriter fw;
    
    private List<String> values;
    private int nextIndex;
    private Node root;
    
    private static class Node {
        private static final byte PREFIX_NODE = -1;
        private Node[] edges; // the chars making up abbreviations
        private char avals; // num values associated with this node
        private int vali; // the value index associated with this node
        private boolean abbrev; // signals this node is ending abbrev. char
        
        /* sets the index of the value associated with this node */
        private Node(int valueIndex) { this.vali = valueIndex; }
        
        /* returns a new edge node at edges[c] if it does not exist, otherwise
         * it returns the already existing edge node (i.e. a prefix node). */
        private Node add(char c, int index) {
            // lazy create next, we don't want leaf nodes with non-null arrays
            if (!hasNext())
                edges = new Node[ASCII_ALPHABET_LENGTH];
            
            // we don't need to offset c here, since 'next()' does that
            Node edge = next(c);
            if (edge == null) {
                edge = new Node(index);
                // need to offset c here
                edges[offsetChar(c)] = edge;
            } else if (!edge.isAbbreviation() && !edge.isPrefix()) {
                // remove quick lookup value from non-abbreviation, prefix nodes
                edge.vali = PREFIX_NODE;
            }
            
            edge.avals += 1;
            
            return edge;
        }
        
        
        private boolean hasNext() { return edges != null; }
        
        private Node next(char c) {
            // offset c to account for ASCII control characters
            return edges[offsetChar(c)];
        }
        
        private int valueIndex() { return vali; }
        
        private boolean isUnique() { return avals == 1; }
        
        // if this node is associated with more than key
        private boolean isPrefix() { return !isUnique(); }
        
        private void markAbbreviation() { abbrev = true; }
        
        private boolean isAbbreviation() { return abbrev; }
        
        
        /* offsets a passed in character to account for ASCII control
         * characters */
        private static char offsetChar(char c) {
            return (char) (c - ASCII_CTRL_OFFSET);
        }
    }
    
    
    /**
     * Constructor that create an empty {@code AbbreviationTrie} instance.
     */
    public AbbreviationTrie() {
        values = new ArrayList<String>();
        nextIndex = 0;
        root = new Node(Node.PREFIX_NODE);
    }
    
    
    /**
     * Adds a key-value mapping to this {@code AbbreviationTrieList} instance.
     * <p>
     * If a passed in {@code key} already has a mapping in this trie, then this
     * method will <i><b>not</b></i> map the {@code key} to the new
     * {@code value}, and this method will return {@link #DUPLICATE_KEY}.
     * <p>
     * If an ASCII control character is read in {@code key} while "put"ting this
     * key-value mapping, then {@link #KEY_HAS_CTRL_CHARS} will be returned and
     * this {@code AbbreviationTrieList} will not be altered.
     * <p>
     * Attempting to "put" another key-value mapping when {@link #size()} equals
     * {@link #MAX_CAPACITY}, will result in {@link #TRIE_IS_FULL} being
     * returned.
     * 
     * @param key
     *              the key portion of the key-value mapping
     * @param value
     *              the value portion of the key-value mapping
     * @return a unique value index, which can be used with
     *         {@link #getValueFromIndex(int)} if this key was added. If this
     *         key was not added, then either {@link #DUPLICATE_KEY},
     *         {@link #KEY_HAS_CTRL_CHARS} or {@link #TRIE_IS_FULL} will be
     *         returned.
     * @throws IllegalArgumentException
     *                                  if {@code key} is empty or if either
     *                                  parameter is {@code null}
     */
    public int put(final String key, final String value) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");
        if (key.isEmpty())
            throw new IllegalArgumentException("key cannot be empty");
        if (value == null)
            throw new IllegalArgumentException("value cannot be null");
        
        if (size() > MAX_CAPACITY)
            return TRIE_IS_FULL;
        
        int index = nextIndex;
        if (values.contains(value)) {
            index = values.indexOf(value);
        }
        
        return put(root, key.toCharArray(), 0, value, index);
    }
    
    /* put helper method, checks for duplicate puts and undoes them */
    private int put(Node node, final char[] key, int cur, String value,
        int index) {
        
        if (key[cur] <= ASCII_CTRL_OFFSET || key[cur] == 127) {
            undoCtrlPut(node, key, cur);
            return KEY_HAS_CTRL_CHARS;
        }
        
        node = node.add(key[cur], index);
        
        if (cur == key.length - 1) {
            // if end node is already an abbreviation then we're adding a
            // duplicate key
            if (node.isAbbreviation()) {
                // undo this put since duplicates mess up internal structure
                undoDupPut(node, key);
                // signal that the key already exists and put was a no-op
                return DUPLICATE_KEY;
            }
            
            // distinguish this node from non-abbreviation nodes
            node.markAbbreviation();
            // update root's associated values count, which size() returns
            root.avals += 1;
            
            // add value to the values list if it not already there
            if (index == nextIndex) {
                values.add(value);
                nextIndex += 1;
            }
            
            return node.valueIndex();
        }
        
        // tail recursion
        return put(node, key, cur + 1, value, index);
    }
    
    /* rolls back 'put's pertaining to control characters */
    private void undoCtrlPut(Node node, final char[] key, int cur) {
        // decrement node's assoc. vals or isPrefix will be invalid
        node.avals -= 1;
        
        if (node.isUnique()) {
            if (node.isAbbreviation()) {
                Node tmp = root;
                for (int i = 0; i < cur - 2; i++) {
                    tmp = tmp.next(key[i]);
                    tmp.avals -= 1;
                    if (tmp.isUnique())
                        tmp.vali = node.valueIndex();
                }
            } else {
                // we have to iterate over node's edges to find its one 'next'
                for (int i = 0, n = node.edges.length; i < n; i++) {
                    if (node.edges[i] != null) {
                        node.vali = node.edges[i].valueIndex();
                        break;
                    }
                }
                
                Node tmp = root;
                // now that we have the old val index, we reset the other nodes
                for (int i = 0; i < cur - 2; i++) {
                    tmp = tmp.next(key[i]);
                    tmp.avals -= 1;
                    if (tmp.isUnique())
                        tmp.vali = node.valueIndex();
                }
            }
        } else {
            // if node is not unique then it's a prefix, which means we can just
            // decrement all of the nodes in key's avals up to node
            Node tmp = root;
            for (int i = 0; i < cur - 2; i++) {
                tmp = tmp.next(key[i]);
                tmp.avals -= 1;
            }
        }
        
    }
    
    /* rolls back 'put's pertaining to duplicates */
    private void undoDupPut(Node abbrNode, final char[] key) {
        Node tmp = root;
        
        // decrement abbrNode's assocVals count or isPrefix will be invalid
        abbrNode.avals -= 1;
        
        // walk from root to abbrNode (exclusive) and reset prefix nodes'
        // associated value counts and values
        for (int i = 0, n = key.length - 1; i < n; i++) {
            tmp = tmp.next(key[i]);
            if (abbrNode.isPrefix())
                tmp.vali = abbrNode.valueIndex();
            tmp.avals -= 1;
        }
    }
    
    
    /**
     * Gets the value associated with the passed in {@code key}.
     * <p>
     * If this {@code AbbreviationTrieList} does not contain a key-value mapping
     * for {@code key}, then this method will return the passed in {@code key}.
     * 
     * @param key
     *            - the key portion of the key-value mapping
     * @return the value associated with {@code key}
     * 
     * @throws IllegalArgumentException
     *                                  if {@code key} is {@code null} or the
     *                                  empty {@code String}
     */
    public String getValueFromKey(final String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");
        if (key.isEmpty())
            throw new IllegalArgumentException("key cannot be empty");
        
        return getValueFromKey(key.toCharArray());
    }
    
    
    /**
     * Gets the value associated with the passed in {@code key}.
     * <p>
     * If this {@code AbbreviationTrieList} does not contain a key-value mapping
     * for {@code key}, then this method will return the {@code String}
     * representation of {@code key}.
     * <p>
     * If the passed in {@code key} has a length of zero, the the empty
     * {@code String} ({@code ""}) will be returned.
     * 
     * @param key
     *            the key portion of the key-value mapping
     * @return the value associated with {@code key}
     * @throws IllegalArgumentException
     *                                  if {@code key} has a length of zero
     */
    public String getValueFromKey(final char[] key) {
        if (key.length == 0)
            throw new IllegalArgumentException("key cannot be empty");
        
        return getValueFromKey(key, 0, key.length);
    }
    
    
    /**
     * Gets the value associated with the passed in {@code key}.
     * <p>
     * If this {@code AbbreviationTrieList} does not contain a key-value mapping
     * for {@code key}, then this method will return the {@code String}
     * representation of {@code key}.
     * 
     * @param key
     *               the key portion of the key-value mapping
     * @param offset
     *               initial character to read from {@code key}
     * @param count
     *               number of characters to read from {@code key}
     * @return the value associated with {@code key}
     * 
     * @throws IllegalArgumentException
     *                                  if {@code offset} is negative, or
     *                                  {@code count} is negative, or
     *                                  {@code key} has a length of zero, or
     *                                  {@code offset + count} is larger than
     *                                  {@code key.length}
     */
    public String getValueFromKey(final char[] key, int offset, int count) {
        int valueIndex = getValueIndex(key, offset, count);
        
        // if key-value mapping is not in this AbbreviationTrieList then return
        // stringified key
        if (valueIndex == VALUE_NOT_FOUND)
            return String.valueOf(key, offset, count);
        
        return getValueFromIndex(valueIndex);
    }
    
    /**
     * Gets this {@code AbbreviationTrieList} instance's index of the value
     * associated with the passed in {@code key}.
     * <p>
     * If this {@code AbbreviationTrieList} does not contain a key-value mapping
     * for {@code key}, then this method will return the {@code String}
     * representation of {@code key}.
     * 
     * @param key
     *               the key portion of the key-value mapping
     * @param offset
     *               initial character to read from {@code key}
     * @param count
     *               number of characters to read from {@code key}
     * @return the value associated with {@code key}
     * 
     * @throws IllegalArgumentException
     *                                  if {@code offset} is negative, or
     *                                  {@code count} is negative, or
     *                                  {@code key} has a length of zero, or
     *                                  {@code offset + count} is larger than
     *                                  {@code key.length}
     */
    public int getValueIndex(final char[] key, int offset, int count) {
        if (offset < 0)
            throw new IllegalArgumentException(
                "offset cannot be less than zero");
        if (count <= 0)
            throw new IllegalArgumentException(
                "count cannot be less than or equal to zero");
        if (key.length == 0)
            throw new IllegalArgumentException("key cannot be empty");
        if (offset + count > key.length)
            throw new IllegalArgumentException(
                "offset + count cannot be larger than key.length");
        
        
        int index = get(root, key, offset, offset + count);
        
        return index;
    }
    
    /* get*(...) helper method - returns a key's value index */
    private int get(Node node, char[] key, int cur, int end) {
        if (node.isUnique() && node != root) { return node.valueIndex(); }
        
        if (cur < end) {
            if (node.hasNext()) {
                Node next = node.next(key[cur]);
                
                if (next == null) {
                    // check for subtle prefixes (ie. 'Atki' and 'AtkiG')
                    if (node.isAbbreviation()) { return node.valueIndex(); }
                    
                    // if next is null and the current node is not an
                    // abbreviation, then no mapping exists
                    return VALUE_NOT_FOUND;
                }
                
                // recursive call with non-null next node
                return get(next, key, cur + 1, end);
                
            } else if (node.isAbbreviation()) { return node.valueIndex(); }
        } else if (cur == end) {
            if (node.isAbbreviation()) { return node.valueIndex(); }
        }
        
        // if we reach this point then there is no key-value mapping
        return VALUE_NOT_FOUND;
    }
    
    /**
     * Retrieves the value associated with the passed in {@code index}.
     * <p>
     * A value's index is generally acquired from one of the
     * {@link #put(String, String) put(...)} methods or from the
     * {@link #getValueIndex(char[], int, int) getValueIndex(...)} method.
     * 
     * @param index
     *              the index of the value to retrieve
     * 
     * @return the value associated with the passed in {@code index}
     * 
     * @throws IllegalArgumentException
     *                                   if the passed in {@code index} is less
     *                                   than zero.
     * @throws IndexOutOfBoundsException
     *                                   if {@code index} is greater than or
     *                                   equal to {@link #size()}.
     */
    public String getValueFromIndex(int index) {
        if (index < 0)
            return "Unknown Abbreviation";
            
        // throw new IllegalArgumentException(
        // "index ( " + index + " ) cannot be less than zero.");
        
        if (index > values.size())
            throw new IndexOutOfBoundsException("index ( " + index
                + " ) is greater equal to size ( " + size() + " ).");
        
        return values.get(index);
    }
    
    /**
     * 
     * @return a copy of this trie's underlying index reference list, at the
     *         time of the call
     */
    public List<String> getValues() { return new ArrayList<String>(values); }
    
    /**
     * Returns the number of key-value mappings in this
     * {@code AbbreviationTrieList}.
     * 
     * @return the number of key-value mappings in this instance
     */
    public int size() { return root.avals; }
    
    
    /**
     * Returns whether this {@code AbbreviationTrieList} is empty or not.
     * 
     * @return {@code true} if this instance is empty, otherwise {@code false}
     */
    public boolean isEmpty() { return size() == 0; }
    
    
}
