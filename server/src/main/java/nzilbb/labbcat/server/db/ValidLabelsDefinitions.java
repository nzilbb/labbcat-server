//
// Copyright 2024 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with LaBB-CAT; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package nzilbb.labbcat.server.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import nzilbb.ag.Layer;

/**
 * Utility functions for generating predefined valid-label definitions using different encodings.
 * <p> LaBB-CAT extends the {@link Layer#validLabels} funcionality by supporting an
 * alternative layer attribute: <tt>validLabelsDefinition</tt>, which is an array of
 * label definitions, each definition being a map of string to string or integer. Each
 * label definition is expected to have the following attributes:
 * <dl>
 * <dt>label</dt> 
 *  <dd>what the underlying label is in LaBB-CAT (i.e. the DISC label, for a DISC layer)</dd> 
 * <dt>display</dt> 
 *  <dd>the symbol in the transcript, for the label (e.g. the IPA
 *      version of the label)</dd> 
 * <dt>selector</dt> 
 *  <dd>the symbol on the label helper, for the label (e.g. the IPA
 *      version of the label) - if there's no selector specified, then the value for display is
 *      used, and if there's no value for display specified, then there's no option
 *      on the label helper (so that type-able consonants like p, b, t, d etc. don't
 *      take up space on the label helper)</dd> 
 * <dt>description</dt> 
 *  <dd>tool-tip text that appears if you hover the mouse over the IPA symbol in the helper</dd>
 * <dt>category</dt> 
 *  <dd>the broad category of the symbol, for organizing the layout of the helper</dd>
 * <dt>subcategory</dt> 
 *  <dd>the narrower category of the symbol, for listing subgroups of symbols together</dd>
 * <dt>display_order</dt> 
 *  <dd>the order to process/list the labels in</dd>
 * </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */

public class ValidLabelsDefinitions {
  
  /**
   * Add standard ARPAbet-phoneme label definitions to the given map.
   * @param validLabelsDefinition Map containing existing definitions, if any.
   */
  public static void AddARPAbetDefinitions(List<Map<String,Object>> validLabelsDefinition) {
    int display_order = validLabelsDefinition.size();
    
    // vowels
        
    // diphthongs
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EY1");
      put("display", "eɪ"); put("selector", "ˈeɪ"); put("description", "FACE (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EY2");
      put("display", "eɪ"); put("selector", "ˌeɪ"); put("description", "FACE (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EY0");
      put("display", "eɪ"); put("selector", "eɪ"); put("description", "FACE (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AY1");
      put("display", "aɪ"); put("selector", "ˈaɪ"); put("description", "PRICE (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AY2");
      put("display", "aɪ"); put("selector", "ˌaɪ"); put("description", "PRICE (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AY0");
      put("display", "aɪ"); put("selector", "aɪ"); put("description", "PRICE (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "OW1");
      put("display", "əʊ"); put("selector", "ˈəʊ"); put("description", "GOAT (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "OW2");
      put("display", "əʊ"); put("selector", "ˌəʊ"); put("description", "GOAT (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "OW0");
      put("display", "əʊ"); put("selector", "əʊ"); put("description", "GOAT (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "OY1");
      put("display", "ɔɪ"); put("selector", "ˈɔɪ"); put("description", "CHOICE (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "OY2");
      put("display", "ɔɪ"); put("selector", "ˌɔɪ"); put("description", "CHOICE (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "OY0");
      put("display", "ɔɪ"); put("selector", "ɔɪ"); put("description", "CHOICE (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AW1");
      put("display", "aʊ"); put("selector", "ˈaʊ"); put("description", "MOUTH (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AW2");
      put("display", "aʊ"); put("selector", "ˌaʊ"); put("description", "MOUTH (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AW0");
      put("display", "aʊ"); put("selector", "aʊ"); put("description", "MOUTH (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
        
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AA1");
      put("display", "ɑː"); put("selector", "ˈɑː"); put("description", "START (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AA2");
      put("display", "ɑː"); put("selector", "ˌɑː"); put("description", "START (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AA0");
      put("display", "ɑː"); put("selector", "ɑː"); put("description", "START (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IY1");
      put("display", "iː"); put("selector", "ˈiː"); put("description", "FLEECE (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IY2");
      put("display", "iː"); put("selector", "ˌiː"); put("description", "FLEECE (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IY0");
      put("display", "iː"); put("selector", "iː"); put("description", "FLEECE (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UW1");
      put("display", "uː"); put("selector", "ˈuː"); put("description", "GOOSE (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UW2");
      put("display", "uː"); put("selector", "ˌuː"); put("description", "GOOSE (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UW0");
      put("display", "uː"); put("selector", "uː"); put("description", "GOOSE (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "ER1");
      put("display", "ɜː"); put("selector", "ˈɜː"); put("description", "NURSE (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "ER2");
      put("display", "ɜː"); put("selector", "ˌɜː"); put("description", "NURSE (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "ER0");
      put("display", "ɜː"); put("selector", "ɜː"); put("description", "NURSE (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AE1");
      put("display", "æ"); put("selector", "ˈæ"); put("description", "TRAP (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AE2");
      put("display", "æ"); put("selector", "ˌæ"); put("description", "TRAP (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AE0");
      put("display", "æ"); put("selector", "æ"); put("description", "TRAP (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AO1");
      put("display", "ɔː"); put("selector", "ˈɔː"); put("description", "THOUGHT/LOT (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AO2");
      put("display", "ɔː"); put("selector", "ˌɔː"); put("description", "THOUGHT/LOT (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AO0");
      put("display", "ɔː"); put("selector", "ɔː"); put("description", "THOUGHT/LOT (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IH1");
      put("display", "ɪ"); put("selector", "ˈɪ"); put("description", "KIT (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IH2");
      put("display", "ɪ"); put("selector", "ˌɪ"); put("description", "KIT (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IH0");
      put("display", "ɪ"); put("selector", "ɪ"); put("description", "KIT (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EH1");
      put("display", "ɛ"); put("selector", "ˈɛ"); put("description", "DRESS (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EH2");
      put("display", "ɛ"); put("selector", "ˌɛ"); put("description", "DRESS (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EH0");
      put("display", "ɛ"); put("selector", "ɛ"); put("description", "DRESS (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AH1");
      put("display", "ʌ"); put("selector", "ˈʌ"); put("description", "STRUT (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AH2");
      put("display", "ʌ"); put("selector", "ˌʌ"); put("description", "STRUT (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AH0");
      put("display", "ʌ"); put("selector", "ʌ"); put("description", "STRUT (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UH1");
      put("display", "ʊ"); put("selector", "ˈʊ"); put("description", "FOOT (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UH2");
      put("display", "ʊ"); put("selector", "ˌʊ"); put("description", "FOOT (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UH0");
      put("display", "ʊ"); put("selector", "ʊ"); put("description", "FOOT (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AX1");
      put("display", "ə"); put("selector", "ˈə"); put("description", "schwa (primary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AX2");
      put("display", "ə"); put("selector", "ˌə"); put("description", "schwa (secondary lexical stress)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AX0");
      put("display", "ə"); put("selector", "ə"); put("description", "schwa (unstressed)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
        
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IM");
      put("display", "æ̃"); put("description", "timbre (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "IN");
      put("display", "ɑ̃ː"); put("description", "détente (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "VN");
      put("display", "æ̃ː"); put("description", "lingerie (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "ON");
      put("display", "ɒ̃ː"); put("description", "bouillon (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
        
    // syllabics
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "UN");
      put("display", "ŋ̩"); put("description", "bacon (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EM");
      put("display", "m̩"); put("description", "idealism (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EN");
      put("display", "n̩"); put("description", "burden (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "EL");
      put("display", "l̩"); put("description", "dangle (extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "AXR");
      put("display", "ɚ"); put("description", "rhotic schwa - extension to ARPABbet)");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
                
    // consonants

    // fricative

    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "F"); put("display", "f");
      put("category", "CONSONANT"); put("subcategory", "Fricative"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "V"); put("display", "v");
      put("category", "CONSONANT"); put("subcategory", "Fricative"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "S"); put("display", "s");
      put("category", "CONSONANT"); put("subcategory", "Fricative"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "Z"); put("display", "z");
      put("category", "CONSONANT"); put("subcategory", "Fricative"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "TH");
      put("display", "θ"); 
      put("category", "CONSONANT"); put("subcategory", "Fricative");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "DH");
      put("display", "ð"); 
      put("category", "CONSONANT"); put("subcategory", "Fricative");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "SH");
      put("display", "ʃ"); 
      put("category", "CONSONANT"); put("subcategory", "Fricative");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "ZH");
      put("display", "ʒ"); 
      put("category", "CONSONANT"); put("subcategory", "Fricative");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "HH"); put("display", "h");
      put("category", "CONSONANT"); put("subcategory", "Fricative"); }});
    
    // affricates
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "JH");
      put("display", "d͜ʒ"); // put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "Affricate");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "CH");
      put("display", "t͜ʃ"); // put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "Affricate");
    }});
        
    // plosive

    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "TQ");
      put("display", "ʔ"); put("description", "Glottal stop (extension to ARPABbet)");
      put("category", "CONSONANT"); put("subcategory", "Plosive");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "P"); put("display", "p");
      put("category", "CONSONANT"); put("subcategory", "Plosive"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "B"); put("display", "b");
      put("category", "CONSONANT"); put("subcategory", "Plosive"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "T"); put("display", "t");
      put("category", "CONSONANT"); put("subcategory", "Plosive"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "D"); put("display", "d");
      put("category", "CONSONANT"); put("subcategory", "Plosive"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "K"); put("display", "k");
      put("category", "CONSONANT"); put("subcategory", "Plosive"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "G"); put("display", "g");
      put("category", "CONSONANT"); put("subcategory", "Plosive"); }});
    
    // nasal

    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "NG");
      put("display", "ŋ"); 
      put("category", "CONSONANT"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "M"); put("display", "m");
      put("category", "CONSONANT"); put("subcategory", "Nasal"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "N"); put("display", "n");
      put("category", "CONSONANT"); put("subcategory", "Nasal"); }});

    // tap

    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "DX");
      put("display", "ɾ"); put("description", "flap - extension to ARPABbet)");
      put("category", "CONSONANT"); put("subcategory", "Tap");
    }});

    // approximant

    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "R");
      put("display", "ɹ"); //put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "Approximant");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "L"); put("display", "l");
      put("category", "CONSONANT"); put("subcategory", "Approximant"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "Y"); put("display", "j");
      put("category", "CONSONANT"); put("subcategory", "Approximant"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "W"); put("display", "w");
      put("category", "CONSONANT"); put("subcategory", "Approximant"); }});
    
    // set display_order
    for (Map<String,Object> label : validLabelsDefinition) {
      label.put("display_order", ++display_order);
    }
    
  } // end of addDISCLabelDefinitions()

  /**
   * Add standard DISC-phoneme label definitions to the given map.
   * @param validLabelsDefinition Map containing existing definitions, if any.
   */
  public static void AddDISCDefinitions(List<Map<String,Object>> validLabelsDefinition) {
    int display_order = validLabelsDefinition.size();
    
    // vowels
        
    // diphthongs
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "1");
      put("display", "eɪ"); put("description", "FACE");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "2");
      put("display", "aɪ"); put("description", "PRICE");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "5");
      put("display", "əʊ"); put("description", "GOAT");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "4");
      put("display", "ɔɪ"); put("description", "CHOICE");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "6");
      put("display", "aʊ"); put("description", "MOUTH");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "7");
      put("display", "ɪə"); put("description", "NEAR");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "8");
      put("display", "ɛə"); put("description", "SQUARE");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "9");
      put("display", "ʊə"); put("description", "CURE");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "W");
      put("display", "ai"); put("description", "weit (German)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "B");
      put("display", "au"); put("description", "Haut (German)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "X");
      put("display", "ɔy"); put("description", "freut (German)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "L");
      put("display", "œy"); put("description", "huis (Dutch)");
      put("category", "VOWEL"); put("subcategory", "Diphthong");
    }});
        
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "&");
      put("display", "a"); //put("description", "X");h<b>a</b>t</td><td>(German)
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "#");
      put("display", "ɑː"); put("description", "START");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "A");
      put("display", "ɑ"); //put("description", "X"); // k<b>a</b>levala</td><td>(German)
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "E");
      put("display", "ɛ"); // put("description", "X");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});                  
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", ")");
      put("display", "ɛː"); put("description", "Käse (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "i");
      put("display", "iː"); put("description", "FLEECE");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "y");
      put("display", "yː"); put("description", "für (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "o");
      put("display", "oː"); put("description", "boot (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "|");
      put("display", "øː"); put("description", "Möbel (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "^");
      put("display", "œ̃ː"); put("description", "Parfum (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "/");
      put("display", "œ");       put("description", "Götter (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "u");
      put("display", "uː"); put("description", "GOOSE");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "3");
      put("display", "ɜː"); put("description", "NURSE");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "{");
      put("display", "æ"); put("description", "TRAP");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "}");
      put("display", "ʉ"); put("description", "put (Dutch)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "$");
      put("display", "ɔː"); put("description", "THOUGHT");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "I");
      put("display", "ɪ"); put("description", "KIT");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "Y");
      put("display", "ʏ"); put("description", "Pfütze");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "V");
      put("display", "ʌ"); put("description", "STRUT");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "Q");
      put("display", "ɒ"); put("description", "LOT");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "O");
      put("display", "ɔ"); put("description", "Glocke (German)");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "U");
      put("display", "ʊ"); put("description", "FOOT");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "@");
      put("display", "ə"); put("description", "schwa");
      put("category", "VOWEL"); put("subcategory", "Monophthong");
    }});
        
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "c");
      put("display", "æ̃"); put("description", "timbre");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "q");
      put("display", "ɑ̃ː"); put("description", "détente");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "0");
      put("display", "æ̃ː"); put("description", "lingerie");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "~");
      put("display", "ɒ̃ː"); put("description", "bouillon");
      put("category", "VOWEL"); put("subcategory", "Nasal");
    }});
        
    // syllabics
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "C");
      put("display", "ŋ̩"); put("description", "bacon");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "F");
      put("display", "m̩"); put("description", "idealism");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "H");
      put("display", "n̩"); put("description", "burden");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "P");
      put("display", "l̩"); put("description", "dangle");
      put("category", "VOWEL"); put("subcategory", "Syllabic");
    }});
                
    // consonants

    // affricates
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "_");
      put("display", "d͜ʒ"); // put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "J");
      put("display", "t͜ʃ"); // put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "=");
      put("display", "t͜s"); // put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "+");
      put("display", "pf");
      put("category", "CONSONANT"); put("subcategory", "");
    }});
        
    // glottal stop
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "?");
      put("display", "ʔ"); put("description", "Glottal stop");
      put("category", "CONSONANT"); put("subcategory", "");
    }});
         
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "r");
      put("display", "ɹ"); //put("description", "X");
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "N");
      put("display", "ŋ"); 
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "T");
      put("display", "θ"); 
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "D");
      put("display", "ð"); 
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "S");
      put("display", "ʃ"); 
      put("category", "CONSONANT"); put("subcategory", "");
    }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "Z");
      put("display", "ʒ"); 
      put("category", "CONSONANT"); put("subcategory", "");
    }});

    // consontants with the same character in DISC and IPA (no display/selector so there's no picker)
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "p"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "b"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "t"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "d"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "k"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "g"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "l"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "m"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "n"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "f"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "v"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "s"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "z"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "j"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "h"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "w"); put("category", "CONSONANT"); }});
    validLabelsDefinition.add(new HashMap<String,Object>() {{
      put("label", "x"); put("category", "CONSONANT"); }});
    
    // set display_order
    for (Map<String,Object> label : validLabelsDefinition) {
      if (!label.containsKey("display_order")) {
        label.put("display_order", ++display_order);
      }
    } // next label
    
  } // end of addDISCLabelDefinitions()

  /**
   * Add given labels, which are assumed to be IPA, to the given map.
   * @param validLabelsDefinition Map containing existing definitions, if any.
   * @param labels Labels to add.
   */
  public static void AddIPADefinitions(
    List<Map<String,Object>> validLabelsDefinition, List<String> labels) {
    int display_order = validLabelsDefinition.size();
    HashSet<String> existingLabels = new HashSet<String>();
    for (Map<String,Object> definition : validLabelsDefinition) {
      existingLabels.add(""+definition.get("label"));
    }

    Pattern vowelPattern = Pattern.compile("[aeiouyɒɔəɛɜʉʊʎæɐɑɚɪøœʏ]");
    Pattern vowelModifiers = Pattern.compile("[ː˥˦˧˨˩]");
    for (String label : labels) {
      if (!existingLabels.contains(label)) {
        HashMap<String,Object> definition = new HashMap<String,Object>();
        definition.put("label", label);
        definition.put("selector", label);
        definition.put("display_order", ++display_order);
        // does the label contain a vowely symbol?
        if (vowelPattern.matcher(label.toLowerCase()).find()) {
          definition.put("category", "VOWEL");
          if (vowelModifiers.matcher(label).replaceAll("").length() == 1) { // monophthong
            definition.put("subcategory", "Monophthong");
          } else { // diphthong
            definition.put("subcategory", "Diphthong");
          }
        } else { // not vowely
          definition.put("category", "CONSONANT");
        }
        validLabelsDefinition.add(definition);
      } // label not already defined
    } // next label
  }
} // end of class ValidLabelsDefinitions