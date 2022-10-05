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
package org.neo4j.bolt.transaction;

import java.util.Objects;

public class CleanUpConnectionContext
{
    private final String connectionId;

    public CleanUpConnectionContext( String connectionId )
    {
        this.connectionId = connectionId;
    }

    public String connectionId()
    {
        return connectionId;
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        CleanUpConnectionContext that = (CleanUpConnectionContext) o;
        return Objects.equals( connectionId, that.connectionId );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( connectionId );
    }
}
