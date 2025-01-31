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
package org.neo4j.graphdb.traversal;

import org.neo4j.annotations.api.PublicApi;

/**
 * A catalog of convenient uniqueness factories.
 */
@PublicApi
public enum Uniqueness implements UniquenessFactory
{
    /**
     * A node cannot be traversed more than once. This is what the legacy
     * traversal framework does.
     */
    NODE_GLOBAL
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return new GloballyUnique( PrimitiveTypeFetcher.NODE );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },
    /**
     * For each returned node there's a unique path from the start node to it.
     */
    NODE_PATH
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return new PathUnique( PrimitiveTypeFetcher.NODE );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },
    /**
     * This is like {@link Uniqueness#NODE_GLOBAL}, but only guarantees
     * uniqueness among the most recent visited nodes, with a configurable
     * count. Traversing a huge graph is quite memory intensive in that it keeps
     * track of <i>all</i> the nodes it has visited. For huge graphs a traverser
     * can hog all the memory in the JVM, causing {@link OutOfMemoryError}.
     * Together with this {@link Uniqueness} you can supply a count, which is
     * the number of most recent visited nodes. This can cause a node to be
     * visited more than once, but scales infinitely.
     */
    NODE_RECENT
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptIntegerOrNull( optionalParameter );
            return new RecentlyUnique( PrimitiveTypeFetcher.NODE, optionalParameter );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },
    /**
     * Entities on the same level are guaranteed to be unique.
     */
    NODE_LEVEL
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return new LevelUnique( PrimitiveTypeFetcher.NODE );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },

    /**
     * A relationship cannot be traversed more than once, whereas nodes can.
     */
    RELATIONSHIP_GLOBAL
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return new GloballyUnique( PrimitiveTypeFetcher.RELATIONSHIP );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },
    /**
     * For each returned node there's a (relationship wise) unique path from the
     * start node to it.
     */
    RELATIONSHIP_PATH
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return new PathUnique( PrimitiveTypeFetcher.RELATIONSHIP );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return false;
        }
    },
    /**
     * Same as for {@link Uniqueness#NODE_RECENT}, but for relationships.
     */
    RELATIONSHIP_RECENT
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptIntegerOrNull( optionalParameter );
            return new RecentlyUnique( PrimitiveTypeFetcher.RELATIONSHIP, optionalParameter );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },
    /**
     * Entities on the same level are guaranteed to be unique.
     */
    RELATIONSHIP_LEVEL
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return new LevelUnique( PrimitiveTypeFetcher.RELATIONSHIP );
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    },

    /**
     * No restriction (the user will have to manage it).
     */
    NONE
    {
        @Override
        public UniquenessFilter create( Object optionalParameter )
        {
            acceptNull( optionalParameter );
            return notUniqueInstance;
        }

        @Override
        public boolean eagerStartBranches()
        {
            return true;
        }
    };

    private static final UniquenessFilter notUniqueInstance = new NotUnique();

    private static void acceptNull( Object optionalParameter )
    {
        if ( optionalParameter != null )
        {
            throw new IllegalArgumentException( "Only accepts null parameter, was " +
                    optionalParameter );
        }
    }

    private static void acceptIntegerOrNull( Object parameter )
    {
        if ( parameter == null )
        {
            return;
        }
        boolean isDecimalNumber = parameter instanceof Number
                && !( parameter instanceof Float || parameter instanceof Double );
        if ( !isDecimalNumber )
        {
            throw new IllegalArgumentException( "Doesn't accept non-decimal values"
                    + ", like '" + parameter + "'" );
        }
    }
}
