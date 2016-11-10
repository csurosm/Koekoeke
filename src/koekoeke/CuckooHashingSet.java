/*
 * The MIT License
 *
 * Copyright 2016 Mikl&oacute;s Cs&#369;r&ouml;s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package koekoeke;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

/**
 * Cuckoo hashing as introduced by Rasmus Pagh and Flemming Friche Rodler [European Symposium on Algorithms 2001].  
 * Delete and search are worst-case O(1) [involving 1 or 2 table lookups]; insertion is O(1) amortized.  
 * 
 * @author Mikl&oacute;s Cs&#369;r&ouml;s
 */
public class CuckooHashingSet extends AbstractSet<Object> implements Set<Object>
{
    /**
     * Maximum load factor, should be less than 0.5.
     */
    private static final double MAX_LOAD_FACTOR = 0.49;
    private static final double MIN_LOAD_FACTOR = 0.10;
    private int capacity_bits;
    private SingleOccupancyTable table1;
    private SingleOccupancyTable table2;
    private long num_insertions_since_last_rehash = 0L;
    private int max_loops;
    private static final int MAX_LOOP_FACTOR = 6;
    private static final int DEFAULT_CAPACITY_BITS = 10;
    public CuckooHashingSet()
    {
        this(capacity(DEFAULT_CAPACITY_BITS));
    }

    public CuckooHashingSet(int requested_capacity)
    {
        // find smallest power of 2 to accomodate the requested capacity
        this.capacity_bits = 4;
        int capacity = capacity(capacity_bits);
        while (capacity < requested_capacity)
        {
            capacity_bits++;
            capacity += capacity;
        }
        this.table1 = new SingleOccupancyTable(capacity_bits);
        this.table2 = new SingleOccupancyTable(capacity_bits);
        this.max_loops = (capacity_bits * MAX_LOOP_FACTOR);
    }

    
    @Override
    public boolean add(Object x)
    {
        if (contains(x)) return false;

        this.num_insertions_since_last_rehash ++;

        for (int num_tries = 0; num_tries < this.max_loops; num_tries++)
        {
            x = this.table1.put(x);
            if (x == EMPTY) break;
            x = this.table2.put(x);
            if (x == EMPTY)
              break;
        }
        if (x != EMPTY)
        {
          rehash(0);
          return add(x);
        }

        if ( //table1.loadFactor()>=MAX_LOAD_FACTOR || table2.loadFactor()>=MAX_LOAD_FACTOR //
                loadFactor()>= MAX_LOAD_FACTOR 
                || (this.num_insertions_since_last_rehash > (table1.n + table2.n) * (table1.n + table2.n))) 
        {
            rehash(capacity());
        }
        return true;
    }

    @Override
    public boolean contains(Object emt)
    {
        return (table1.contains(emt)) || (table2.contains(emt));
    }

    @Override
    public boolean remove(Object emt)
    {
        
        boolean b = (table1.remove(emt)) || (table2.remove(emt));
        if (b && capacity_bits>DEFAULT_CAPACITY_BITS && loadFactor()<MIN_LOAD_FACTOR)
                rehash(-capacity()/2);
        return b;
    }
    
    @Override
    public void clear()
    {
        CuckooHashingSet clean_slate = new CuckooHashingSet();
        this.table1 = clean_slate.table1;
        this.table2 = clean_slate.table2;

        this.num_insertions_since_last_rehash = 0L;
    }

    private void rehash(int capacity_delta)
    {
        int new_capacity = capacity() + capacity_delta;
//        System.out.println("#*CH.re "+capacity_bits+" -> "+newcapbits);

        Object[] old1 = table1.elements;
        Object[] old2 = table2.elements;

        CuckooHashingSet shiny_new_table = new CuckooHashingSet(new_capacity);
        int i=0;
        for (int n1=0; n1<table1.size(); n1++)
        {
            while (old1[i]==EMPTY) i++;
            shiny_new_table.add(old1[i++]);
        }
        i=0;
        for (int n2=0; n2<table2.size(); n2++)
        {
            while (old2[i]==EMPTY) i++;
            shiny_new_table.add(old2[i++]);
        }
        this.table1 = shiny_new_table.table1;
        this.table2 = shiny_new_table.table2;
        this.num_insertions_since_last_rehash = 0L;
        this.capacity_bits = shiny_new_table.capacity_bits;
        this.max_loops = shiny_new_table.max_loops;
//        System.out.println("#*CH.re DONE "+capacity_bits+" -> "+newcapbits);
    }    
    
    @Override
    public int size()
    {
        return table1.size() + table2.size();
    }
    
    private static int capacity(int table_cap_bits)
    {
        return 2*(1<<table_cap_bits);
    }
    
    private int capacity()
    { 
        int c = capacity(capacity_bits);
        assert (c==table1.elements.length + table2.elements.length);
        return c;
    }
    
    private double loadFactor()
    {
        return size()/((double)capacity());
    }
    
    @Override
    public Iterator<Object> iterator()
    {
        return new Iterator<Object>()
        {
            private final Iterator<Object> iter1 = table1.iterator();
            private final Iterator<Object> iter2 = table2.iterator();

            @Override
            public boolean hasNext()
            {
              return (this.iter1.hasNext()) || (this.iter2.hasNext());
            }

            @Override
            public Object next()
            {
              if (iter1.hasNext()) return iter1.next();
              return iter2.next();
            }
        };
    }

    /**
     * Sentinel element used to denote empty cells in {@link CuckooHashingSet.SingleOccupancyTable}
     */
    private static final Object EMPTY = new Object();
    /**
     * A primitive hash table without resolution collision
     */
    private static final class SingleOccupancyTable
    {
        /**
         * Factor used in hash function
         */
        private final int hfact1;
        /**
         * Factor used in hash function
         */
        private final int hfact2;
        /**
         * Factor used in hash function
         */
        private final int hfact3;
        /**
         * Bit shift used in hash function
         */
        private final int hshift;
        /**
         * SingleOccupancyTable for storing the elements
         */
        private final Object[] elements;
        /**
         * Number of elements stored here, at long precision for convenience
         */
        private long n;

        SingleOccupancyTable(int cap_bits)
        {
            this.elements = new Object[1 << cap_bits];
            this.hshift = (32 - cap_bits);
            Random RND = new Random();
            this.hfact1 = RND.nextInt();
            this.hfact2 = RND.nextInt();
            this.hfact3 = RND.nextInt();
            clear();
        }
        
        /**
         * Random hash function used by Pagh and Radler.
         * @param x
         * @return hash value indexing {@link #elements}
         */
        int getIndex(int x)
        {
            return (x * this.hfact1 ^ x * this.hfact2 ^ x * this.hfact3) >>> this.hshift;
        }
        
        /**
         * Clears the table by using a sentinel element 
         */
        void clear()
        {
            Arrays.fill(this.elements, CuckooHashingSet.EMPTY);
            this.n = 0L;
        }
        
        /**
         * Puts an object into its cell
         * @param o null OK
         * @return the element that occupied the cell; {@link #EMPTY} denotes unoccupied cell
         */
        Object put(Object o)
        {
            assert (o != CuckooHashingSet.EMPTY);

            int mwmw = o == null ? 0 : getIndex(o.hashCode());
            Object nunu = this.elements[mwmw];
            this.elements[mwmw] = o;
            this.n += (nunu == CuckooHashingSet.EMPTY ? 1 : 0);
            return nunu;
        }

        /**
         * Checks the content for an object's primary cell
         * @param o null OK
         * @return the element that occupies its cell; {@link #EMPTY} denotes unoccupied cell
         */
        Object whoSleepsInMyBed(Object o)
        {
            int mwmw = o == null ? 0 : getIndex(o.hashCode());
            Object nunu = this.elements[mwmw];
            return nunu;
        }

        /**
         * Whether an equal element is stored in this table
         * @param o null OK
         * @return true if already inserted
         */
        boolean contains(Object o)
        {
            assert (o != CuckooHashingSet.EMPTY);
            Object nunu = whoSleepsInMyBed(o);
            return ((nunu == null) && (o == nunu)) || ((nunu != null) && (nunu.equals(o)));
        }

   
        /**
         * Load factor in this table (number of occupied cells / capacity)
         * @return value between 0.0 and 1.0 (&lt;0.5, in usable implementations) 
         */
        private double loadFactor()
        {
            return ((double)this.n) / ((double)this.elements.length);
        }

        boolean remove(Object o)
        {
            assert (o != CuckooHashingSet.EMPTY);

            int mwmw = o == null ? 0 : getIndex(o.hashCode());
            Object nunu = this.elements[mwmw];
            if (((nunu == null) && (o == nunu)) || ((nunu != null) && (nunu.equals(o))))
            {
                this.elements[mwmw] = CuckooHashingSet.EMPTY;
                this.n --;
                return true;
            }
            return false;
        }

        int size()
        {
            return (int)this.n;
        }

        Iterator<Object> iterator()
        {
            class TableIterator implements Iterator<Object>
            {
                private int cell_idx;

                TableIterator()
                {
                    this.cell_idx = 0;
                    forwardToNext();
                }

                private void forwardToNext()
                {
                    while ((this.cell_idx < elements.length) && (elements[cell_idx] == CuckooHashingSet.EMPTY))
                        this.cell_idx++;
                }

                @Override
                public boolean hasNext()
                {
                    return this.cell_idx < elements.length;
                }

                @Override
                public Object next()
                {
                    Object x = elements[this.cell_idx];
                    forwardToNext();
                    return x;
                }            
            }
            
            return new TableIterator();
        }
        
    }
}