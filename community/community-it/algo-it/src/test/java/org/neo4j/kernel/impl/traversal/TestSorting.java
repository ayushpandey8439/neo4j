/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.traversal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.helpers.collection.Iterables;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphdb.traversal.Evaluators.excludeStartPosition;
import static org.neo4j.graphdb.traversal.Sorting.endNodeProperty;

class TestSorting extends TraversalTestBase
{
    @Test
    void sortFriendsByName()
    {
        /*
         *      (Abraham)
         *          |
         *         (me)--(George)--(Dan)
         *          |        |
         *       (Zack)---(Andreas)
         *                   |
         *              (Nicholas)
         */

        String me = "me";
        String abraham = "Abraham";
        String george = "George";
        String dan = "Dan";
        String zack = "Zack";
        String andreas = "Andreas";
        String nicholas = "Nicholas";
        String knows = "KNOWS";
        createGraph( triplet( me, knows, abraham ), triplet( me, knows, george), triplet( george, knows, dan ),
                triplet( me, knows, zack ), triplet( zack, knows, andreas ), triplet( george, knows, andreas ),
                triplet( andreas, knows, nicholas ) );

        try ( Transaction tx = beginTx() )
        {
            List<Node> nodes = asNodes( tx, abraham, george, dan, zack, andreas, nicholas );
            assertEquals( nodes, Iterables.asCollection( tx.traversalDescription().evaluator( excludeStartPosition() )
                    .sort( endNodeProperty( "name" ) ).traverse( getNodeWithName( tx, me ) ).nodes() ) );
            tx.commit();
        }
    }

    private static List<Node> asNodes( Transaction tx, String abraham, String george, String dan, String zack, String andreas, String nicholas )
    {
        List<String> allNames = new ArrayList<>( asList( abraham, george, dan, zack, andreas, nicholas ) );
        Collections.sort( allNames );
        List<Node> all = new ArrayList<>();
        for ( String name : allNames )
        {
            all.add( getNodeWithName( tx, name ) );
        }
        return all;
    }

    private static String triplet( String i, String type, String you )
    {
        return i + " " + type + " " + you;
    }
}
