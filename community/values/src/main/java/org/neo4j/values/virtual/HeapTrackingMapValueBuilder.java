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
package org.neo4j.values.virtual;

import static org.neo4j.memory.HeapEstimator.SCOPED_MEMORY_TRACKER_SHALLOW_SIZE;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;

import org.neo4j.collection.trackable.HeapTrackingCollections;
import org.neo4j.collection.trackable.HeapTrackingUnifiedMap;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.AnyValue;

public class HeapTrackingMapValueBuilder implements AutoCloseable
{
    /**
     * Start building a list of unknown size with heap tracking
     * Values added to the list will have their heap usage estimated and tracked in the give memory tracker.
     *
     * Caveat: When calling build() the ownership of the internal heap-tracking list will be transferred
     * to the returned MapValue, and it will carry the heap usage accumulated by the builder as its payload size.
     * But to be accounted for, this MapValue will need to be measured and allocated in a memory tracker.
     * (This is in alignment with other AnyValues)
     * Beware that in the time window between closing the builder and allocating the returned MapValue,
     * the total memory usage may either be underestimated (un-accounted) or overestimated (double counted) depending
     * on the order of events.
     *
     * @return a new heap tracking builder
     */
    public static HeapTrackingMapValueBuilder newHeapTrackingMapValueBuilder( MemoryTracker memoryTracker )
    {
        return new HeapTrackingMapValueBuilder( memoryTracker );
    }

    private static final long SHALLOW_SIZE = shallowSizeOfInstance( HeapTrackingMapValueBuilder.class );

    // We wait to track memory (bytes) below this threshold (see `unAllocatedHeapSize`).
    private static final long HEAP_SIZE_ALLOCATION_THRESHOLD = 4096;

    private final HeapTrackingUnifiedMap<String, AnyValue> values;
    private final MemoryTracker scopedMemoryTracker;

    /*
     * Estimated heap usage in bytes of items that has been added to the
     * builder but not yet accounted for in the memory tracker.
     *
     * We have seen queries that spend a lot of time to allocate heap in the
     * memory tracker when adding lots of small items (RollupApply micro
     * benchmark). This is an optimisation for such cases.
     */
    private long unAllocatedHeapSize;

    public HeapTrackingMapValueBuilder( MemoryTracker memoryTracker )
    {
        scopedMemoryTracker = memoryTracker.getScopedMemoryTracker();
        scopedMemoryTracker.allocateHeap( SHALLOW_SIZE + SCOPED_MEMORY_TRACKER_SHALLOW_SIZE );
        values = HeapTrackingCollections.newMap( scopedMemoryTracker );
    }

    public void put( String key, AnyValue value )
    {
        unAllocatedHeapSize += value.estimatedHeapUsage();
        if ( unAllocatedHeapSize >= HEAP_SIZE_ALLOCATION_THRESHOLD )
        {
            scopedMemoryTracker.allocateHeap( unAllocatedHeapSize );
            unAllocatedHeapSize = 0;
        }

        values.put( key, value );
    }

    public MapValue build()
    {
        scopedMemoryTracker.allocateHeap( unAllocatedHeapSize );
        unAllocatedHeapSize = 0;
        return new MapValue.MapWrappingMapValue( values, payloadSize() );
    }

    public MapValue buildAndClose()
    {
        final var value = build();
        close();
        return value;
    }

    private long payloadSize()
    {
        // The shallow size should not be transferred to the MapValue (but the ScopedMemoryTracker is)
        return unAllocatedHeapSize + scopedMemoryTracker.estimatedHeapMemory() - SHALLOW_SIZE;
    }

    @VisibleForTesting
    public long getUnAllocatedHeapSize()
    {
        return unAllocatedHeapSize;
    }

    @Override
    public void close()
    {
        scopedMemoryTracker.close();
    }
}
