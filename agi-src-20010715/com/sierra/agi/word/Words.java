/*
 * Words.java
 */

package com.sierra.agi.word;

import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;
import com.sierra.agi.Context;
import com.sierra.agi.misc.ByteCasterStream;

/**
 * Stores Words of the game.
 * <P>
 * <B>Word File Format</B><BR>
 * The words.tok file is used to store the games vocabulary, i.e. the dictionary
 * of words that the interpreter understands. These words are stored along with
 * a word number which is used by the said test commands as argument values for
 * that command. Many words can have the same word number which basically means
 * that these words are synonyms for each other as far as the game is concerned.
 * </P><P>
 * The file itself is both packed and encrypted. Words are stored in alphabetic
 * order which is required for the compression method to work.
 * </P><P>
 * <B>The first section</B><BR>
 * At the start of the file is a section that is always 26x2 bytes long. This
 * section contains a two byte entry for every letter of the alphabet. It is
 * essentially an index which gives the starting location of the words beginning
 * with the corresponding letter.
 * </P><P>
 * <TABLE BORDER=1>
 * <THEAD><TR><TD>Byte</TD><TD>Meaning</TD></TR></THEAD>
 * <TBODY>
 * <TR><TD>0-1</TD><TD>Hi and then Lo byte for 'A' offset</TD></TR>
 * <TR><TD COLSPAN=2>...</TD></TR>
 * <TR><TD>50-51</TD><TD>Hi and then Lo byte for 'Z' offset</TD></TR>
 * <TR><TD>52</TD><TD>Words section</TD></TR>
 * </TBODY></TABLE>
 * </P><P>
 * The important thing to note from the above is that the 16 bit words are
 * big-endian (HI-LO). The little endian (LO-HI) byte order convention used
 * everywhere else in the AGI system is not used here. For example, 0x00 and
 * 0x24 means 0x0024, not 0x2400. Big endian words are used later on for word
 * numbers as well.
 * </P><P>
 * All offsets are taken from the beginning of the file. If no words start with
 * a particular letter, then the offset in that field will be 0x0000.
 * </P><P>
 * <B>The words section</B><BR>
 * Words are stored in a compressed way in which each word will use part of the
 * previous word as a starting point for itself. For example, "forearm" and
 * "forest" both have the prefix "fore". If "forest" comes immediately after
 * "forearm", then the data for "forest" will specify that it will start with
 * the first four characters of the previous word. Whether this method is used
 * for further confusion for would be cheaters or whether it is to help in the
 * searching process, I don't yet know, but it most certainly isn't purely for
 * compression since the words.tok file is usally quite small and no attempt is
 * made to compress any of the larger files (before AGI version 3 that is).
 * </P><P>
 * <TABLE BORDER=1>
 * <THEAD><TR><TD>Byte</TD><TD>Meaning</TD></TR></THEAD>
 * <TBODY>
 * <TR><TD>0</TD><TD>Number of characters to include from start of prevous word</TD></TR>
 * <TR><TD>1</TD><TD>Char 1 (xor 0x7F gives the ASCII code for the character)</TD></TR>
 * <TR><TD>2</TD><TD>Char 2</TD></TR>
 * <TR><TD COLSPAN=2>...</TD></TR>
 * <TR><TD>n</TD><TD>Last char</TD></TR>
 * <TR><TD>n + 1</TD><TD>Wordnum (LO-HI) -- see below</TD></TR>
 * </TBODY></TABLE>
 * </P><P>
 * If a word does not use any part of the previous word, then the prefix field
 * is equal to zero. This will always be the case for the first word starting
 * with a new letter. There is nothing to indicate where the words starting with
 * one letter finish and the next set starts, infact the words section is just
 * one continuous chain of words conforming to the above format. The index
 * section mentioned earlier is not needed to read the words in which suggests
 * that the whole words.tok format is organised to find words quickly.
 * </P><P>
 * <B>A note about word numbers</B><BR>
 * Some word numbers have special meaning. They are listed below:
 * </P><P>
 * <TABLE BORDER=1>
 * <THEAD><TR><TD>Word #</TD><TD>Meaning</TD></TR></THEAD>
 * <TBODY>
 * <TR><TD>0</TD><TD>Words are ignored (e.g. the, at)</TD></TR>
 * <TR><TD>1</TD><TD>Anyword</TD></TR>
 * <TR><TD>9999</TD><TD>ROL (Rest Of Line) -- it does matter what the rest of the input list is</TD></TR>
 * </TBODY></TABLE>
 * </P>
 * @author Dr. Z, Lance Ewing (Documentation)
 * @version 0.00.00.01
 */
public class Words extends Object
{
    protected Hashtable wordHash = new Hashtable(800);
    
    /** Creates a new Word container. */
    protected Words()
    {
    }
    
    public static Words loadWords(Context context, InputStream stream) throws IOException
    {
        Words w = new Words();
        
        w.loadWordTable(context, stream);
        return w;
    }
    
    /**
     * Read a AGI word table.
     *
     * @param context Can be null. (Ignored for now)
     * @param stream  Stream from where to read the words.
     */
    protected int loadWordTable(Context context, InputStream stream) throws IOException
    {
        ByteCasterStream bstream = new ByteCasterStream(stream);
        String           prev    = null;
        String           curr;
        int              i, wordNum, wordCount;
        
        stream.skip(52);
        wordCount = 0;
        
        while (true)
        {
            i = stream.read();
            
            if (i < 0)
            {
                break;
            }
            else if (i > 0)
            {
                curr = prev.substring(0, i);
            }
            else
            {
                curr = new String();
            }
            
            while (true)
            {
                i = stream.read();
                
                if (i <= 0)
                {
                    break;
                }
                else
                {
                    curr += (char)((i ^ 0x7F) & 0x7F);
                    
                    if (i >= 0x7F)
                    {
                        break;
                    }
                }
            }
            
            if (i <= 0)
            {
                break;
            }
            
            wordNum = bstream.hiloReadUnsignedShort();
            prev    = curr;
            
            addWord(wordNum, curr);
            wordCount++;
        }
        
        stream.close();
        return wordCount;
    }
    
    public void addWord(int wordNum, String word)
    {
        Word w = (Word)wordHash.get(word);

        if (w != null)
        {
            return;
        }
        
        w        = new Word();
        w.number = wordNum;
        w.text   = word;
                
        wordHash.put(word, w);
    }
    
    public int findWord(String word)
    {
        Word w = (Word)wordHash.get(word);
        
        if (w == null)
        {
            return -1;
        }
        
        return w.number;
    }
    
    public Enumeration elements()
    {
        return wordHash.elements();
    }
    
    public Object[] getWordList()
    {
        Object[] words = wordHash.values().toArray();
        
        java.util.Arrays.sort(words, new WordSorter());
        return words;
    }
    
    class WordSorter implements java.util.Comparator
    {
        public int compare(Object o1, Object o2)
        {
            return ((Word)o1).text.compareTo(((Word)o2).text);
        }
    }
}