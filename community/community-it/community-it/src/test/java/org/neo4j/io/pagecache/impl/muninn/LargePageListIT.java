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
package org.neo4j.io.pagecache.impl.muninn;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import org.neo4j.io.ByteUnit;
import org.neo4j.io.mem.MemoryAllocator;
import org.neo4j.memory.EmptyMemoryTracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.io.ByteUnit.GibiByte;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

class LargePageListIT
{
    @Test
    void veryLargePageListsMustBeFullyAccessible()
    {
        // We need roughly 2 GiBs of memory for the meta-data here, which is why this is an IT and not a Test.
        // We add one extra page worth of data to the size here, to avoid ending up on a "convenient" boundary.
        int pageSize = (int) ByteUnit.kibiBytes( 8 );
        long pageCacheSize = ByteUnit.gibiBytes( 513 ) + pageSize;
        int pages = Math.toIntExact( pageCacheSize / pageSize );

        MemoryAllocator mman = MemoryAllocator.createAllocator( GibiByte.toBytes( 2 ), EmptyMemoryTracker.INSTANCE );
        SwapperSet swappers = new SwapperSet();
        long victimPage = VictimPageReference.getVictimPage( pageSize, INSTANCE );

        PageList pageList = new PageList( pages, pageSize, mman, swappers, victimPage, Long.BYTES );

        // Verify we end up with the correct number of pages.
        assertThat( pageList.getPageCount() ).isEqualTo( pages );

        // Spot-check the accessibility in the bulk of the pages.
        IntStream.range( 0, pages / 32 ).parallel().forEach( id -> verifyPageMetaDataIsAccessible( pageList, id * 32 ) );

        // Thoroughly check the accessibility around the tail end of the page list.
        IntStream.range( pages - 2000, pages ).parallel().forEach( id -> verifyPageMetaDataIsAccessible( pageList, id ) );
    }

    private static void verifyPageMetaDataIsAccessible( PageList pageList, int id )
    {
        long ref = pageList.deref( id );
        PageList.incrementUsage( ref );
        PageList.incrementUsage( ref );
        assertFalse( PageList.decrementUsage( ref ) );
        assertTrue( PageList.decrementUsage( ref ) );
        assertEquals( id, pageList.toId( ref ) );
    }
}
