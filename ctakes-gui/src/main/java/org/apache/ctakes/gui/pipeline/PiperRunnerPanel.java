package org.apache.ctakes.gui.pipeline;


import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.core.pipeline.PiperFileRunner;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.gui.component.*;
import org.apache.ctakes.gui.pipeline.bit.parameter.ParameterCellRenderer;
import org.apache.ctakes.gui.pipeline.piper.PiperFileView;
import org.apache.ctakes.gui.pipeline.piper.PiperTextFilter;
import org.apache.ctakes.gui.util.IconLoader;
import org.apache.log4j.Logger;
import org.apache.uima.fit.descriptor.ConfigurationParameter;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 4/18/2017
 */
final public class PiperRunnerPanel extends JPanel {

   static private final Logger LOGGER = Logger.getLogger( "PiperRunnerPanel" );

   static private final String CLI_EXTENSION = "piper_cli";

   private final JFileChooser _piperChooser = new JFileChooser();
   private final JFileChooser _parmChooser = new JFileChooser();
   private DefaultStyledDocument _piperDocument;
   private PiperTextFilter _piperTextFilter;

   private String _piperPath = "";

   private JTextPane _textPane;
   private JTable _cliTable;

   private JButton _openButton;
   private JButton _saveButton;
   private JButton _parmButton;
   private JButton _runButton;

   private final String[] STANDARD_CHARS = { "i", "o", "s", "l", "-user", "-pass", "-xmiOut" };

   private final Map<String, String> _charToName = new HashMap<>( STANDARD_CHARS.length );
   private final Map<String, String> _charToValue = new HashMap<>( STANDARD_CHARS.length );

   private final java.util.List<Character> _cliChars = new ArrayList<>();
   private final Map<Character, String> _cliCharToName = new HashMap<>();
   private final Map<Character, String> _cliCharToValue = new HashMap<>();


   PiperRunnerPanel() {
      super( new BorderLayout() );
      final String[] STANDARD_NAMES = { "InputDirectory", "OutputDirectory", "SubDirectory",
            "LookupXml", "UMLS Username", "UMLS Password", "XMI Output" };
      for ( int i = 0; i < STANDARD_CHARS.length; i++ ) {
         _charToName.put( STANDARD_CHARS[ i ], STANDARD_NAMES[ i ] );
      }

      final JSplitPane logSplit = new PositionedSplitPane( JSplitPane.VERTICAL_SPLIT );
      logSplit.setTopComponent( createMainPanel() );
      logSplit.setBottomComponent( LoggerPanel.createLoggerPanel() );
      logSplit.setDividerLocation( 0.6d );

      add( createToolBar(), BorderLayout.NORTH );

      add( logSplit, BorderLayout.CENTER );
      SwingUtilities.invokeLater( new ButtonIconLoader() );

      _piperChooser.setFileFilter( new FileNameExtensionFilter( "Pipeline Definition (Piper) File", "piper") );
      _piperChooser.setFileView( new PiperFileView() );
      _parmChooser.setFileFilter( new FileNameExtensionFilter( "Pipeline Definition (Piper) Parameter File", CLI_EXTENSION ) );
      _parmChooser.setFileView( new PiperFileView() );
   }

   private JToolBar createToolBar() {
      final JToolBar toolBar = new JToolBar();
      toolBar.setFloatable( false );
      toolBar.setRollover( true );
      toolBar.addSeparator( new Dimension( 10, 0 ) );
      _openButton = addButton( toolBar, "Open Existing Piper File" );
      _openButton.addActionListener( new OpenPiperAction() );
      toolBar.addSeparator( new Dimension( 50, 0 ) );
      _parmButton = addButton( toolBar, "Open Parameter File" );
      _parmButton.addActionListener( new OpenParmAction() );
      _saveButton = addButton( toolBar, "Save Parameter File" );
      _saveButton.addActionListener( new SaveParmAction() );
      toolBar.add( Box.createHorizontalGlue() );
      _runButton = addButton( toolBar, "Run Current Piper File" );
      _runButton.addActionListener( new RunAction() );
      _runButton.setEnabled( false );
      return toolBar;
   }

   static private JButton addButton( final JToolBar toolBar, final String toolTip ) {
      final JButton button = new JButton();
      button.setFocusPainted( false );
      // prevents first button from having a painted border
      button.setFocusable( false );
      button.setToolTipText( toolTip );
      toolBar.add( button );
      toolBar.addSeparator( new Dimension( 10, 0 ) );
      return button;
   }

   private JComponent createMainPanel() {
      final JComponent westPanel = createWestPanel();
      final JComponent eastPanel = createEastPanel();
      final JSplitPane mainSplit = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, westPanel, eastPanel );
      mainSplit.setDividerLocation( 0.5 );
      return mainSplit;
   }

   private JComponent createEastPanel() {
      _piperDocument = new DefaultStyledDocument();
      _piperTextFilter = new PiperTextFilter( _piperDocument );
      _textPane = new JTextPane( _piperDocument );
      _textPane.putClientProperty( "caretWidth", 2 );
      _textPane.setCaretColor( Color.MAGENTA );
      _textPane.setEditable( false );
      final JScrollPane scroll = new JScrollPane( _textPane );
      final TextLineNumber lineNumber = new TextLineNumber( _textPane, 2 );
      scroll.setRowHeaderView( lineNumber );
      scroll.setMinimumSize( new Dimension( 100, 10 ) );
      return scroll;
   }

   private JComponent createWestPanel() {
      return new JScrollPane( createCliTable() );
   }

   private JComponent createCliTable() {
      _cliTable = new SmoothTipTable( new CliOptionModel() );
      _cliTable.setRowHeight( 20 );
      _cliTable.setAutoResizeMode( JTable.AUTO_RESIZE_LAST_COLUMN );
      _cliTable.getColumnModel().getColumn( 0 ).setPreferredWidth( 100 );
      _cliTable.getColumnModel().getColumn( 0 ).setMaxWidth( 100 );
      _cliTable.getColumnModel().getColumn( 1 ).setMaxWidth( 50 );
      _cliTable.getColumnModel().getColumn( 3 ).setMaxWidth( 25 );
      _cliTable.setRowSelectionAllowed( true );
      _cliTable.setCellSelectionEnabled( true );
      _cliTable.setDefaultRenderer( ConfigurationParameter.class, new ParameterCellRenderer() );
      final FileTableCellEditor fileEditor = new FileTableCellEditor();
      fileEditor.getFileChooser().setFileSelectionMode( JFileChooser.FILES_AND_DIRECTORIES );
      _cliTable.setDefaultRenderer( File.class, fileEditor );
      _cliTable.setDefaultEditor( File.class, fileEditor );
      ListSelectionModel selectionModel = _cliTable.getSelectionModel();
      selectionModel.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
      return _cliTable;
   }


   // -i, -o, -s, -l --user --pass --xmiOut
   private final class CliOptionModel implements TableModel {
      private final String[] COLUMN_NAMES = { "Parameter Name", "Option", "Value", "" };
      private final Class<?>[] COLUMN_CLASSES = { String.class, String.class, String.class, File.class };
      private final EventListenerList _listenerList = new EventListenerList();
      public int getRowCount() {
         return STANDARD_CHARS.length + _cliChars.size();
      }
      @Override
      public int getColumnCount() {
         return 4;
      }
      @Override
      public String getColumnName( final int column ) {
         return COLUMN_NAMES[ column ];
      }
      @Override
      public Class<?> getColumnClass( final int column ) {
         return COLUMN_CLASSES[ column ];
      }
      @Override
      public Object getValueAt( final int row, final int column ) {
         if ( column == 0 ) {
            if ( row < STANDARD_CHARS.length ) {
               return _charToName.get( STANDARD_CHARS[ row ] );
            }
            final Character c = _cliChars.get( row - STANDARD_CHARS.length );
            return _cliCharToName.getOrDefault( c, "Unknown Name" );
         } else if ( column == 1 ) {
            if ( row < STANDARD_CHARS.length ) {
               return '-' + STANDARD_CHARS[ row ];
            }
            return '-' + _cliChars.get( row - STANDARD_CHARS.length );
         } else if ( column == 2 ) {
            if ( row < STANDARD_CHARS.length ) {
               return _charToValue.getOrDefault( STANDARD_CHARS[ row ], "" );
            }
            return _cliCharToValue.getOrDefault( _cliChars.get( row - STANDARD_CHARS.length ), "" );
         } else if ( column == 3 ) {
            final String path = (String)getValueAt( row, 1 );
            return new File( path );
         }
         return "ERROR";
      }
      @Override
      public boolean isCellEditable( final int row, final int column ) {
         return column != 0;
      }
      @Override
      public void setValueAt( final Object aValue, final int row, final int column ) {
         if ( column == 2 ) {
            if ( row < STANDARD_CHARS.length ) {
               _charToValue.put( STANDARD_CHARS[ row ], (String) aValue );
            } else {
               _cliCharToValue.put( _cliChars.get( row - STANDARD_CHARS.length ), (String) aValue );
            }
            fireTableChanged( new TableModelEvent( this, row, row, column ) );
         } else if ( column == 3 && File.class.isInstance( aValue ) ) {
            final String path = ((File)aValue).getPath();
            if ( row < STANDARD_CHARS.length ) {
               _charToValue.put( STANDARD_CHARS[ row ], path );
            } else {
               _cliCharToValue.put( _cliChars.get( row - STANDARD_CHARS.length ), path );
            }
            fireTableChanged( new TableModelEvent( this, row, row, 1 ) );
         }
      }
      @Override
      public void addTableModelListener( final TableModelListener listener ) {
         _listenerList.add( TableModelListener.class, listener );
      }
      @Override
      public void removeTableModelListener( final TableModelListener listener ) {
         _listenerList.remove( TableModelListener.class, listener );
      }
      private void fireTableChanged( final TableModelEvent event ) {
         // Guaranteed to return a non-null array
         Object[] listeners = _listenerList.getListenerList();
         // Process the listeners last to first, notifying
         // those that are interested in this event
         for ( int i = listeners.length - 2; i >= 0; i -= 2 ) {
            if ( listeners[ i ] == TableModelListener.class ) {
               ((TableModelListener)listeners[ i + 1 ]).tableChanged( event );
            }
         }
      }
   }





   private final class OpenPiperAction implements ActionListener {
      @Override
      public void actionPerformed( final ActionEvent event ) {
         final int option = _piperChooser.showOpenDialog( null );
         if ( option != JFileChooser.APPROVE_OPTION ) {
            return;
         }
         loadPiperFile( _piperChooser.getSelectedFile() );
      }
   }

   public void loadPiperFile( final File file ) {
      loadPiperFile( file.getPath() );
   }

   public void loadPiperFile( final String path ) {
      final PiperFileReader reader = new PiperFileReader();
      String text = loadPiperText( reader, path );
      try {
         _piperDocument.remove( 0, _piperDocument.getLength() );
         _piperDocument.insertString( 0, text, null );
      } catch ( BadLocationException blE ) {
         LOGGER.warn( blE.getMessage() );
      }
      _cliChars.clear();
      _cliCharToName.clear();
      _cliCharToValue.clear();
      if ( !loadPiperCli( reader, text ) ) {
         error( "Could not load Piper File: " + path );
         return;
      }
      _piperPath = path;
      _runButton.setEnabled( true );
      _cliTable.revalidate();
      _cliTable.repaint();
   }

   private String loadPiperText( final PiperFileReader reader, final String filePath ) {
      LOGGER.info( "Loading Piper File: " + filePath );
      try {
         final String loadPath = reader.getPiperPath( filePath );
         return Files.lines( Paths.get( loadPath ) ).collect( Collectors.joining( "\n" ) );
      } catch ( IOException ioE ) {
         error( ioE.getMessage() );
         return "";
      }
   }

   private boolean loadPiperCli( final PiperFileReader reader, final String text ) {
      for ( String line : text.split( "\\n" ) ) {
         if ( line.startsWith( "cli " ) && line.length() > 5 ) {
            final String[] allValues = line.substring( 4 ).split( "\\s+" );
            for ( String allValue : allValues ) {
               final String[] values = allValue.split( "=" );
               if ( values.length != 2 || values[ 1 ].length() != 1 ) {
                  error( "Illegal cli values: " + line );
                  return false;
               }
               if ( _cliCharToName.put( values[ 1 ].charAt( 0 ), values[ 0 ] ) != null ) {
                  error( "Repeated cli value: " + line );
                  return false;
               }
               _cliChars.add( values[ 1 ].charAt( 0 ) );
            }
         } else if ( line.startsWith( "package " ) && line.length() > 9 ) {
            final String packagePath = line.substring( 8 );
            reader.addUserPackage( packagePath );
         } else if ( line.startsWith( "load " ) && line.length() > 6 ) {
            final String filePath = line.substring( 5 ).trim();
            final String subText = loadPiperText( reader, filePath );
            if ( subText.isEmpty() ) {
               error( "Piper File not found: " + filePath );
               return false;
            }
            if ( !loadPiperCli( reader, subText ) ) {
               error( "Could not load Piper File: " + filePath );
               return false;
            }
         }
      }
      return true;
   }


   private void error( final String error ) {
      LOGGER.error( error );
      JOptionPane.showMessageDialog( this, error, "Piper File Error", JOptionPane.ERROR_MESSAGE );
      _piperPath = "";
      _cliChars.clear();
      _cliCharToName.clear();
      _cliCharToValue.clear();
      _runButton.setEnabled( false );
      _cliTable.revalidate();
      _cliTable.repaint();
   }

   private final class OpenParmAction implements ActionListener {
      @Override
      public void actionPerformed( final ActionEvent event ) {
         final int option = _parmChooser.showOpenDialog( null );
         if ( option != JFileChooser.APPROVE_OPTION ) {
            return;
         }
         final File file = _parmChooser.getSelectedFile();
         openParameterFile( file );
      }
   }

   public void openParameterFile( final File file ) {
      openParameterFile( file.getPath() );
   }

   public void openParameterFile( final String path ) {
      LOGGER.info( "Loading Piper cli values file: " + path );
      try {
         final String filePath = FileLocator.getFullPath( path );
         Files.lines( Paths.get( filePath ) ).forEach( this::loadValueLine );
      } catch ( IOException ioE ) {
         LOGGER.error( ioE.getMessage() );
      }
      _cliTable.revalidate();
      _cliTable.repaint();
   }

   private void loadValueLine( final String line ) {
      final String trimmed = line.trim();
      if ( trimmed.isEmpty() || trimmed.startsWith( "//" ) || trimmed.startsWith( "#" ) ) {
         return;
      }
      final String[] values = trimmed.split( "=" );
      if ( values.length != 2 ) {
         LOGGER.error( "Invalid parameter line: " + line );
         return;
      }
      if ( values[ 0 ].startsWith( "-" ) ) {
         final String chars = values[ 0 ].substring( 1 );
         if ( _charToName.containsKey( chars ) ) {
            _charToValue.put( chars, values[ 1 ] );
         } else if ( chars.length() == 1 && _cliChars.contains( chars.charAt( 0 ) ) ) {
            _cliCharToValue.put( chars.charAt( 0 ), values[ 1 ] );
         } else {
            LOGGER.warn( "Unknown parameter: " + values[0] );
         }
      } else if ( _charToName.containsValue( values[ 0 ] ) ) {
         final String chars = getStringKey( _charToName, values[ 0 ] );
         _charToValue.put( chars, values[ 1 ] );
      } else if ( _cliCharToName.containsValue( values[ 0 ] ) ) {
         _cliCharToValue.put( getCharKey( _cliCharToName, values[ 0 ] ), values[ 1 ] );
      } else {
         LOGGER.warn( "Unknown parameter: " + values[ 0 ] );
      }
   }

   private String getStringKey( final Map<String, String> map, final String value ) {
      return map.entrySet().stream()
            .filter( e -> value.equals( e.getValue() ) )
            .map( Map.Entry::getKey )
            .findAny()
            .orElse( "" );
   }

   private Character getCharKey( final Map<Character, String> map, final String value ) {
      return map.entrySet().stream()
            .filter( e -> value.equals( e.getValue() ) )
            .map( Map.Entry::getKey )
            .findAny()
            .orElse( ' ' );
   }

   private final class SaveParmAction implements ActionListener {
      @Override
      public void actionPerformed( final ActionEvent event ) {
         final int option = _parmChooser.showSaveDialog( null );
         if ( option != JFileChooser.APPROVE_OPTION ) {
            return;
         }
         final File file = _parmChooser.getSelectedFile();
         String path = file.getPath();
         if ( !path.endsWith( "." + CLI_EXTENSION ) ) {
            path += "." + CLI_EXTENSION;
         }
         LOGGER.info( "Saving Piper cli values file: " + path );
         final Collection<String> lines = Arrays.stream( STANDARD_CHARS )
               .filter( c -> _charToValue.get( c ) != null )
               .filter( c -> !_charToValue.get( c ).isEmpty() )
               .map( c -> "// " + _charToName.get( c ) + "\n-" + c + "=" + _charToValue.get( c ) + "\n" )
               .collect( Collectors.toList() );
         _cliChars.stream()
               .filter( c -> _cliCharToValue.get( c ) != null )
               .filter( c -> !_cliCharToValue.get( c ).isEmpty() )
               .map( c -> "// " + _cliCharToName.get( c ) + "\n-" + c + "=" + _cliCharToValue.get( c ) + "\n" )
               .forEach( lines::add );
         try {
            Files.write( Paths.get( path ),  lines, StandardOpenOption.CREATE, StandardOpenOption.WRITE );
         } catch ( IOException ioE ) {
            LOGGER.error( ioE.getMessage() );
         }
      }
   }

   private final class RunAction implements ActionListener {
      @Override
      public void actionPerformed( final ActionEvent event ) {
         if ( _runButton == null ) {
            return;
         }
         LOGGER.info( "Running Piper File ..." );
         final ExecutorService executor = Executors.newSingleThreadExecutor();
         executor.execute( new PiperFileRunnable() );
         executor.shutdown();
      }
   }

   private class PiperFileRunnable implements Runnable {
      @Override
      public void run() {
         final JFrame frame = (JFrame)SwingUtilities.getRoot( PiperRunnerPanel.this );
         frame.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
         DisablerPane.getInstance().setVisible( true );
         final java.util.List<String> args = new ArrayList<>();
         args.add( 0, "-p" );
         args.add( 1, _piperPath );
         for ( String standard : STANDARD_CHARS ) {
            final String value = _charToValue.get( standard );
            if ( value != null && !value.isEmpty() ) {
               args.add( "-" + standard );
               args.add( value );
            }
         }
         for ( Character cli : _cliChars ) {
            final String value = _cliCharToValue.get( cli );
            if ( value != null && !value.isEmpty() ) {
               args.add( "-" + cli );
               args.add( value );
            }
         }
         try {
            PiperFileRunner.run( args.toArray( new String[ args.size() ] ) );
         } catch ( Throwable t ) {
            LOGGER.error( "Pipeline Run caused Exception:", t );
         }
         DisablerPane.getInstance().setVisible( false );
         frame.setCursor( Cursor.getDefaultCursor() );
      }
   }

   /**
    * Simple Runnable that loads an icon
    */
   private final class ButtonIconLoader implements Runnable {
      @Override
      public void run() {
         final String dir = "org/apache/ctakes/gui/pipeline/icon/";
         final String openPng = "OpenPiper.png";
         final String parmPng = "BoxOfStuff.png";
         final String savePng = "Package.png";
         final String runPng = "RunPiper.png";
         final Icon openIcon = IconLoader.loadIcon( dir + openPng );
         final Icon parmIcon = IconLoader.loadIcon( dir + parmPng );
         final Icon saveIcon = IconLoader.loadIcon( dir + savePng );
         final Icon runIcon = IconLoader.loadIcon( dir + runPng );
         _openButton.setIcon( openIcon );
         _parmButton.setIcon( parmIcon );
         _saveButton.setIcon( saveIcon );
         _runButton.setIcon( runIcon );
      }
   }

}
