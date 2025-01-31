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
package org.neo4j.kernel.api.impl.schema.populator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.kernel.api.impl.schema.SchemaIndex;
import org.neo4j.kernel.api.impl.schema.writer.LuceneIndexWriter;
import org.neo4j.kernel.api.index.UniqueIndexSampler;
import org.neo4j.kernel.impl.index.schema.IndexUpdateIgnoreStrategy;
import org.neo4j.storageengine.api.NodePropertyAccessor;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.values.storable.Value;

/**
 * A {@link LuceneIndexPopulatingUpdater} used for unique Lucene schema indexes.
 * Verifies uniqueness of added and changed values when closed using
 * {@link SchemaIndex#verifyUniqueness(NodePropertyAccessor, int[], List)} method.
 */
public class UniqueLuceneIndexPopulatingUpdater extends LuceneIndexPopulatingUpdater
{
    private final int[] propertyKeyIds;
    private final SchemaIndex luceneIndex;
    private final NodePropertyAccessor nodePropertyAccessor;
    private final UniqueIndexSampler sampler;

    private final List<Value[]> updatedValueTuples = new ArrayList<>();

    public UniqueLuceneIndexPopulatingUpdater( LuceneIndexWriter writer, int[] propertyKeyIds, SchemaIndex luceneIndex,
                                               NodePropertyAccessor nodePropertyAccessor, UniqueIndexSampler sampler,
                                               IndexUpdateIgnoreStrategy ignoreStrategy )
    {
        super( writer, ignoreStrategy );
        this.propertyKeyIds = propertyKeyIds;
        this.luceneIndex = luceneIndex;
        this.nodePropertyAccessor = nodePropertyAccessor;
        this.sampler = sampler;
    }

    @Override
    protected void added( ValueIndexEntryUpdate<?> update )
    {
        sampler.increment( 1 );
        updatedValueTuples.add( update.values() );
    }

    @Override
    protected void changed( ValueIndexEntryUpdate<?> update )
    {
        updatedValueTuples.add( update.values() );
    }

    @Override
    protected void removed( ValueIndexEntryUpdate<?> update )
    {
        sampler.increment( -1 );
    }

    @Override
    public void close() throws IndexEntryConflictException
    {
        try
        {
            luceneIndex.verifyUniqueness( nodePropertyAccessor, propertyKeyIds, updatedValueTuples );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }
}
