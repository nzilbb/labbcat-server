//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with nzilbb.ag; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

package nzilbb.labbcat.server.search;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.junit.*;
import static org.junit.Assert.*;

public class TestSearchTask {

  /** Ensure that search validation works. */
  @Test public void validation() {
    Matrix m = new Matrix()
      .addColumn(new Column()
                 .addLayerMatch(new LayerMatch()
                                  .setId("orthography")
                                  .setPattern("the"))
                 .setAdj(3))
      .addColumn(new Column()
                 .addLayerMatch(new LayerMatch()
                                  .setId("phonology")
                                  .setPattern("[aeiou].*")
                                  .setAnchorStart(true))
                 .addLayerMatch(new LayerMatch()
                                  .setId("syllableCount")
                                  .setMin(2.0)
                                  .setMax(3.0)
                                  .setAnchorEnd(true))
                 .addLayerMatch(new LayerMatch()
                                  .setId("orthography")
                                  .setTarget(true)));

    SearchTask search = new SearchTask() { protected void search() throws Exception {} };
    assertEquals("No matrix",
                 "Search matrix was not specified", search.validate());
    
    search.setMatrix(new Matrix());
    assertEquals("Empty matrix",
                 "Search matrix was not specified", search.validate());

    search.getMatrix().addColumn(
      new Column().addLayerMatch(new LayerMatch().setId("orthography")));
    assertEquals("No pattern specified",
                 "No search text was specified", search.validate());
    
    search.getMatrix().addColumn(
      new Column().addLayerMatch(
        new LayerMatch().setId("orthography").setPattern("some pattern")));
    assertNull("Valid matrix", search.validate());
  }

}
