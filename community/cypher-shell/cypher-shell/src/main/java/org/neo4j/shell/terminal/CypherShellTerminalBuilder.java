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
package org.neo4j.shell.terminal;

import org.jline.keymap.KeyMap;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Macro;
import org.jline.reader.Reference;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

import org.neo4j.shell.log.Logger;
import org.neo4j.shell.parser.ShellStatementParser;
import org.neo4j.util.VisibleForTesting;

/**
 * Builder for CypherShellTerminals
 */
public class CypherShellTerminalBuilder
{
    private Logger logger;
    private OutputStream out;
    private InputStream in;
    private boolean isInteractive = true;
    private boolean dumb;
    private Supplier<SimplePrompt> simplePromptSupplier = SimplePrompt::defaultPrompt;

    /** if enabled is true, this is an interactive terminal that supports user input */
    public CypherShellTerminalBuilder interactive( boolean isInteractive )
    {
        this.isInteractive = isInteractive;
        return this;
    }

    public CypherShellTerminalBuilder logger( Logger logger )
    {
        this.logger = logger;
        return this;
    }

    /** Set explicit streams, for testing purposes */
    @VisibleForTesting
    public CypherShellTerminalBuilder streams( InputStream in, OutputStream out )
    {
        this.in = in;
        this.out = out;
        return this;
    }

    /** Create a dumb terminal, for testing purposes */
    @VisibleForTesting
    public CypherShellTerminalBuilder dumb()
    {
        this.dumb = true;
        return this;
    }

    @VisibleForTesting
    public CypherShellTerminalBuilder simplePromptSupplier( Supplier<SimplePrompt> simplePromptSupplier )
    {
        this.simplePromptSupplier = simplePromptSupplier;
        return this;
    }

    public CypherShellTerminal build()
    {
        assert logger != null;

        try
        {
            return isInteractive ? buildJlineBasedTerminal() : nonInteractiveTerminal();
        }
        catch ( IOException e )
        {
            if ( isInteractive )
            {
                logger.printError( "Failed to create interactive terminal, fallback to non-interactive mode" );
            }
            return nonInteractiveTerminal();
        }
    }

    private CypherShellTerminal nonInteractiveTerminal()
    {
        return new WriteOnlyCypherShellTerminal( out != null ? new PrintStream( out ) : System.out );
    }

    public CypherShellTerminal buildJlineBasedTerminal() throws IOException
    {
        var jLineTerminal = TerminalBuilder.builder();

        jLineTerminal.nativeSignals( true );
        jLineTerminal.paused( true ); // Needed for SimplePrompt to work.

        if ( in != null )
        {
            jLineTerminal.streams( in, out );
        }

        if ( dumb )
        {
            var attributes = new Attributes();
            attributes.setLocalFlag( Attributes.LocalFlag.ECHO, false );
            jLineTerminal.jna( false ).jansi( false ); // Certain environments (osx) can't handle jna/jansi mode when running tests in maven
            jLineTerminal.dumb( true ).type( Terminal.TYPE_DUMB ).attributes( attributes );
        }

        var reader = LineReaderBuilder.builder()
            .terminal( jLineTerminal.build() )
            .parser( new CypherJlineParser( new ShellStatementParser() ) )
            .completer( NullCompleter.INSTANCE )
            .history( new DefaultHistory() ) // The default history is in-memory until we set history file variable
            .expander( new JlineTerminal.EmptyExpander() )
            .option( LineReader.Option.DISABLE_EVENT_EXPANSION, true ) // Disable '!' history expansion
            .option( LineReader.Option.DISABLE_HIGHLIGHTER, true )
            .variable( LineReader.DISABLE_COMPLETION, true )
            .build();

        bindKeyPadKeys( reader );

        return new JlineTerminal( reader, isInteractive, logger, simplePromptSupplier );
    }

    public static CypherShellTerminalBuilder terminalBuilder()
    {
        return new CypherShellTerminalBuilder();
    }

    // Extra key bindings required to make putty work
    // (https://github.com/jline/jline3/issues/160#issuecomment-328866357).
    private static void bindKeyPadKeys( final LineReader lineReader )
    {
        final var keyMap = lineReader.getKeyMaps().get( LineReader.MAIN );// 0 . Enter
        keyMap.bind( new Macro( KeyMap.translate( "." ) ), KeyMap.translate("^[On" ) ); // .
        keyMap.bind( new Macro( KeyMap.translate( "^M" ) ), KeyMap.translate("^[OM" ) ); // Enter

        keyMap.bind( new Macro( KeyMap.translate( "0" ) ), KeyMap.translate("^[Op" ) ); // 0
        keyMap.bind( new Macro( KeyMap.translate( "1" ) ), KeyMap.translate("^[Oq" ) ); // 1
        keyMap.bind( new Macro( KeyMap.translate( "2" ) ), KeyMap.translate( "^[Or" ) ); // 2
        keyMap.bind( new Macro( KeyMap.translate( "3" ) ), KeyMap.translate("^[Os" ) ); // 3
        keyMap.bind( new Macro( KeyMap.translate( "4" ) ), KeyMap.translate("^[Ot" ) ); // 4
        keyMap.bind( new Macro( KeyMap.translate( "5" ) ), KeyMap.translate("^[Ou" ) ); // 5
        keyMap.bind( new Macro( KeyMap.translate( "6" ) ), KeyMap.translate("^[Ov" ) ); // 6
        keyMap.bind( new Macro( KeyMap.translate( "7" ) ), KeyMap.translate("^[Ow" ) ); // 7
        keyMap.bind( new Macro( KeyMap.translate( "8" ) ), KeyMap.translate("^[Ox" ) ); // 8
        keyMap.bind( new Macro( KeyMap.translate( "9" ) ), KeyMap.translate("^[Oy" ) ); // 9

        keyMap.bind( new Macro( KeyMap.translate( "+" ) ), KeyMap.translate("^[Ol" ) ); // +
        keyMap.bind( new Macro( KeyMap.translate( "-" ) ), KeyMap.translate("^[OS" ) ); // -
        keyMap.bind( new Macro( KeyMap.translate( "*" ) ), KeyMap.translate("^[OR" ) ); // *
        keyMap.bind( new Macro( KeyMap.translate( "/" ) ), KeyMap.translate("^[OQ" ) ); // /

        keyMap.bind( new Reference( LineReader.BEGINNING_OF_LINE ), KeyMap.translate("\033[1~" ) ); // Home
        keyMap.bind( new Reference( LineReader.END_OF_LINE ), KeyMap.translate("\033[4~" ) ); // End
    }
}
