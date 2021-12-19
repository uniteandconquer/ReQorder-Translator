package bundletranslator;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

public class GUI extends javax.swing.JFrame
{
    private final DefaultListModel keyListModel;
    //these 3 lists contain comments and new lines 
    private List<String> linesOriginal;
    private List<String> linesTarget;
    private List<String> linesExport;
    //these 3 lists are filtered for comments and new lines
    private ArrayList<String> keys;
    private ArrayList<String> originals;
    private String[] translations;
    
    private ArrayList<FormatWarning> warnings;
    
    private boolean isButtonEvent;    
    private int lastSelectedIndex = 0;
    private int lastEditedIndex;
    private int currentWarningIndex = 0;
    
    private File m_SourceFile;
    
    public GUI()
    {
        initComponents();
        
        statusLabel.setText("");
        warningLabel.setText("");
        
        keyList.addListSelectionListener((javax.swing.event.ListSelectionEvent event) ->
        {
            //skip execution through listener if button triggered event
            if(isButtonEvent)
            {
                isButtonEvent = false;
                return;
            }
            //Don't trigger event for the item that was deselected
            if (event.getValueIsAdjusting())
                return;
            if (keyList.getSelectedIndex() < 0)
                return;
                        
            for (int i = 0; i < keys.size(); i++)
            {
                String key = keys.get(i);

                try
                {
                    if (key.equals(keyList.getSelectedValue()))
                    {
                        int lineCount = targetTextArea.getText().split("\r\n|\r|\n",-1).length - 1;
                        if(lineCount > 0)
                        {
                            JOptionPane.showMessageDialog(this, "Found " + lineCount + " lines in this text. The text should be one line only");
                            isButtonEvent = true;//avoid recursion
                            keyList.setSelectedIndex(lastSelectedIndex);
                            return;
                        }   
                        
                        saveToFile();
                        updateStatus();
                        sourceTextArea.setText(originals.get(i));
                        targetTextArea.setText(translations[i]);
                        targetTextArea.requestFocus();   
                        lastEditedIndex = lastSelectedIndex;
                        lastSelectedIndex = i;
                        return;
                    }                    
                }
                catch (NullPointerException e)
                {
                    JOptionPane.showMessageDialog(this, "You need to load or create a target file before you can start editing");
                }
            }
        });

        keyListModel = (DefaultListModel) keyList.getModel();
        
        initFrame();
        
        File testFile = new File(System.getProperty("user.dir") + "/translated/Language.properties");
        if(testFile.exists())
        {
            m_SourceFile = testFile;
            loadSourceFile(testFile);
        }
        
        testFile = new File(System.getProperty("user.dir") + "/translated/target.tmp");
        if(testFile.exists())
        {
            loadTargetFile(testFile);
        }
        else
        {
            createNewTargetFile();
            helpDialog.setVisible(true);
        }
    }
    
     private void initFrame()
    {
        //put the frame at middle of the screen,add icon and set visible
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();    
        setLocation(dim.width / 2 - getSize().width / 2, dim.height / 2 - getSize().height / 2);
        helpDialog.setModal(true);
        helpDialog.pack();
        helpDialog.setLocation(dim.width / 2 - helpDialog.getSize().width / 2, dim.height / 2 - helpDialog.getSize().height / 2);       

        SwingUtilities.invokeLater(() ->
        {            
            helpScrollPane.getVerticalScrollBar().setValue(0);
        });
    }
    
    private void loadSourceFile(File sourceFile)
    {
        keyListModel.clear();
        
        try
        {
            linesOriginal = Files.readAllLines(sourceFile.toPath(), StandardCharsets.UTF_8);
            linesExport = new ArrayList<>();
            keys = new ArrayList<>();
            originals = new ArrayList<>();

            for (String line : linesOriginal)
            {
                String[] split = line.split("=", 2);
                String key = split[0];                

                if (key.isBlank() || key.startsWith("#"))
                {
                    linesExport.add(split[0]);
                    continue;
                }
                else
                    linesExport.add(split[0] + "=");

                keyListModel.addElement(key);
                keys.add(key);
                originals.add(split[1]);
            }  
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private void loadTargetFile(File file)
    {
        try
            {                         
                //saving with the filechooser savedialog probably saves in iso, the utf and ascii wouldn't load properly StandardCharsets.ISO_8859_1); //  
                linesTarget = Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
                if(linesTarget.size() > linesOriginal.size())
                {                   
                    
                    boolean prevWasNewline = false;
                    int removedCount = 0;
                    
                    for(int i = linesTarget.size() - 1; i >= 0; i--)
                    {
                        String line = linesTarget.get(i);
                        
                        if(line.isBlank())
                        {                            
                            if(prevWasNewline)
                            {
                                linesTarget.remove(i);
                                removedCount++;
                            }
                            
                            prevWasNewline = true;
                        }
                        else
                        {
                            prevWasNewline = false;
                        }                            
                            
                    }
                    
                    if(linesTarget.size() <= linesOriginal.size())
                        JOptionPane.showMessageDialog(this, 
                            "Target file was modified\n\nTarget file had more lines than the source file.\nRemoved " + removedCount + "  empty lines\n");
                    else
                    {                        
                        JOptionPane.showMessageDialog(this, 
                                "Operation aborted\n\nTarget file has too many lines\n"
                                        + "compared to the source file.Modification unsuccessful\n"
                                        + "It's also possible that the source file is invalid \n");
                        return;
                    }                    
                }
                
                translations = new String[originals.size()];
                Arrays.fill(translations, "");               
                
                
                for(String line : linesTarget)
                {       
                    String[] split = line.split("=",2);
                    String key = split[0];                    
                    
                    if(key.isBlank() || key.startsWith("#"))
                        continue;
                    
                    for(String originalKey : keys)
                    {
                        if(originalKey.equals(key))
                        {
                            translations[keys.indexOf(originalKey)] = split[1];
                            break;
                        }
                    }
                    for(int i = 0; i < linesExport.size(); i++)
                    {
                        String expLine = linesExport.get(i);
                        String expKey = expLine.split("=",2)[0];
                        
                        if(key.equals(expKey))
                        {
                            linesExport.set(i, key + "=" + split[1]);      
                            break;
                        }
                    }
                }
                
                targetTextArea.setText(translations[0]);
                keyList.setSelectedIndex(0);
                updateStatus();
                
                if(file.getName().equals("target.tmp"))                    
                    JOptionPane.showMessageDialog(jSplitPane1, "Previous session loaded succesfully");
                
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
    }
    
    private void saveTranslation(int index, String translation)
    {
        try
        {
            translations[index] = translation;
            
        }
        catch (NullPointerException e)
        {
            JOptionPane.showMessageDialog(this, "No key selected in the list");
        }
    }
    
    /**Gets called any time the user selects another key to edit by using next/previous buttons*/
    private void keyChangedWithButton(int index)
    {
        if(translations == null || translations.length == 0)
        {
            JOptionPane.showMessageDialog(this, "You need to load or create a target file before you can start editing");
            return;
        }
        
        int lineCount = targetTextArea.getText().split("\r\n|\r|\n",-1).length - 1;
        if(lineCount > 0)
        {
            JOptionPane.showMessageDialog(this, "Found " + lineCount + " lines in this text. The text should be one line only");
            return;
        }          
        
        isButtonEvent = true;
        saveToFile();//must be before set index
        keyList.setSelectedIndex(index);
        updateStatus();//must be after setting index for accurate warning labels
        keyList.ensureIndexIsVisible(keyList.getSelectedIndex());
        sourceTextArea.setText(originals.get(index));
        targetTextArea.setText(translations[index]);
        targetTextArea.requestFocus(); 
        
        lastEditedIndex = lastSelectedIndex;
        lastSelectedIndex = keyList.getSelectedIndex();
    }
    
    private void saveToFile()
    {         
        if(targetTextArea.getText().isBlank())
            return;        
        
        translations[lastSelectedIndex] = targetTextArea.getText();
                
        PrintWriter out;

        try (OutputStream os = new FileOutputStream(System.getProperty("user.dir") + "/translated/target.tmp"))
        {
            out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
            String key = keys.get(lastSelectedIndex).split("=")[0];
            String newLine = key + "=" + translations[lastSelectedIndex];
            for(int i = 0; i < linesExport.size(); i++)//can't use index, keys list has different order
            {
                String line = linesExport.get(i);
                String compare = line.split("=")[0];

                if(compare.equals(key))
                {
                    linesExport.set(i, newLine);
                    out.println(newLine);
                    continue;
                }

                out.println(line);
            }
            out.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private void updateStatus()
    {     
        warnings = new ArrayList<>();
        
        int emptyCount = 0;
        int newLineCount = 0;
        int missingBreaks = 0;
        int missingPlaceHolders = 0;
        int missingQuotes = 0;
        int missingHtmlClose = 0;
        int missingHtmlOpen = 0;
        
        for(int i = 0; i < originals.size(); i++)
        {
            String original = originals.get(i);
            String translation = translations[i];
            
            if(translation.isBlank())
            {
                emptyCount++;
                continue;
            }
            
            int lineCount = translation.split("\r\n|\r|\n",-1).length - 1;
            if(lineCount > 0)
            {
                newLineCount += lineCount;
            }  
            
            int lineTags = newLineCount(original); 
            int lineTagsT = newLineCount(translation); 
            
            int htmlOpen = 0;
            if(original.contains("<html><div style=") && !translation.contains("<html><div style="))
                htmlOpen++;            
            
            int htmlClose = 0;
            if(original.contains("</div></html>") && !translation.contains("</div></html>"))
                htmlClose++;            
            
            int breaks = original.split("<br/>", -1).length-1;
            int breaksT = translation.split("<br/>", -1).length -1;            
            missingBreaks += (breaks - breaksT);
            
            int placeHolders= original.split("%%",-1).length -1;
            int placeHoldersT = translation.split("%%",-1).length -1;            
            missingPlaceHolders += (placeHolders - placeHoldersT);      
                        
            int quotes = original.split("'",-1).length -1;
            int quotesT = translation.split("'",-1).length -1;
            missingQuotes += (quotes - quotesT);
            
            if(lineCount + (lineTags - lineTagsT) + htmlOpen + htmlClose + (breaks - breaksT) + 
                    (placeHolders - placeHoldersT) + (quotes - quotesT)  != 0)
                warnings.add(new FormatWarning(
                        i, 
                        lineCount, 
                        (lineTags - lineTagsT),
                        (breaks - breaksT), 
                        (placeHolders - placeHoldersT), 
                        htmlOpen,
                        htmlClose,
                        (quotes - quotesT)));
        }        
        
        if(!warnings.isEmpty())
        {
            warningLabel.setText("Index " + keyList.getSelectedIndex() + ": This translation has no warnings");
            for(FormatWarning warning : warnings)
            {
                if(keyList.getSelectedIndex() == warning.index)
                {
                    updateWarningLabel(warning);
                    currentWarningIndex = warnings.indexOf(warning);
                    break;
                }
            }
            
        }
        else
            warningLabel.setText("Index " + keyList.getSelectedIndex() + ": This translation has no warnings");
        
        statusLabel.setText("Empty lines remaining: " + emptyCount 
                + " | Warnings: " + warnings.size());
//                + " | New lines found: " + newLineCount
//                + " | Breaks missing: " + missingBreaks
//                + " | Place holders missing: " + missingPlaceHolders
//                + " | Html tags missing: " + missingHtml
//                + " | Div tags missing: " + missingDiv
//                + " | Quotes missing: " + missingQuotes );
    }
    
    private void updateWarningLabel(FormatWarning warning)
    {                
        String labelString = "Index " + keyList.getSelectedIndex() +  " translation: ";
        
        if(warning.newLines > 0)
            labelString += warning.newLines + " too many new lines";   
        
        if(warning.lineTags < 0)
            labelString += " | " + Math.abs(warning.lineTags) + " too many line tags (\\n)";
        if(warning.lineTags > 0)
            labelString += " | missing " + warning.lineTags + " line tags (\\n)"; 
        
        if(warning.breaks < 0)
            labelString += " | " + Math.abs(warning.breaks) + " too many line breaks (<br/>)";
        if(warning.breaks > 0)
            labelString += " | missing " + warning.breaks + " line breaks (<br/>)";
        
        if(warning.placeHolders < 0)
            labelString += " | "  + Math.abs(warning.placeHolders) + " too many place holders (%%) ";
        if(warning.placeHolders > 0)
            labelString += " | missing " + warning.placeHolders + " place holders (%%) ";
        
        if(warning.htmlOpen < 0)
            labelString += " | "  + Math.abs(warning.htmlOpen) + " too many html open tags (<html><div style=) ";
        if(warning.htmlOpen > 0)
            labelString += " | missing " + warning.htmlOpen + " html open tags (<html><div style=) ";
        
        if(warning.htmlClose < 0)
            labelString += " | "  + Math.abs(warning.htmlClose) + " too many html close tags (</div></html>) ";
        if(warning.htmlClose > 0)
            labelString += " | missing " + warning.htmlClose + " html close tags (</div></html>) ";
        
        if(warning.singleQuotes < 0)
            labelString += " | " + Math.abs(warning.singleQuotes) + " too many single quotes (ignore if not code related)";
        if(warning.singleQuotes > 0)
            labelString += " | missing " + warning.singleQuotes + " single quotes (ignore if not code related)";
        
        if(labelString.equals("Index " + keyList.getSelectedIndex() +  "translation: "))
            warningLabel.setText("Index " + keyList.getSelectedIndex() + ": This translation has no warnings");
        else
            warningLabel.setText(labelString);
    }
    
    private int newLineCount(String s)
    {
        int count = 0;
        char last = '.';
        
        for(char c : s.toCharArray())
        {
            if(last == '\\' && c ==  'n' )
                count++;
            last = c;
        }
        
        return count;
    }
    
    private void createNewTargetFile()
    {
        translations = new String[originals.size()];
        Arrays.fill(translations, "");
        warnings = new ArrayList<>();
        targetTextArea.setText("");
        keyList.setSelectedIndex(0);
        updateStatus();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        helpDialog = new javax.swing.JDialog();
        helpScrollPane = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        continueButton = new javax.swing.JButton();
        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPane3 = new javax.swing.JScrollPane();
        keyList = new javax.swing.JList(new DefaultListModel());
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        sourceTextArea = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        targetTextArea = new javax.swing.JTextArea();
        loadSourceFileButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        nextEmptyButton = new javax.swing.JButton();
        copyButton = new javax.swing.JButton();
        previousButton = new javax.swing.JButton();
        previousEmptyButton = new javax.swing.JButton();
        loadTargetFileButton = new javax.swing.JButton();
        newFileButton = new javax.swing.JButton();
        pasteButton = new javax.swing.JButton();
        statusLabel = new javax.swing.JLabel();
        prevWarningButton = new javax.swing.JButton();
        nextWarningButton = new javax.swing.JButton();
        warningLabel = new javax.swing.JLabel();
        saveFileButton = new javax.swing.JButton();
        backButton = new javax.swing.JButton();
        helpButton = new javax.swing.JButton();

        helpDialog.setMinimumSize(new java.awt.Dimension(600, 500));

        jTextArea1.setEditable(false);
        jTextArea1.setColumns(20);
        jTextArea1.setFont(new java.awt.Font("Verdana", 0, 12)); // NOI18N
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText("Important notes for the translator:\n\nIn order to preserve the functionality of the ReQorder application, some rules of thumb have to be adhered to:\n\n- You should only translate the actual words, any symbols or code shoud not be altered\n\n- %% are placeholder symbols for dynamically generated text \n\n- <br> and <br/> are line breaks \n\n- <html> and <div> are used for alligning text in the dialogs\n\n- Single quotes ( ' ) are used to insert text entries into databases in some cases, so it's imperative to not ommit them or add them unless neccesary\n\n- New lines are not allowed, if you hit enter/return you will automatically be directed to the next empty entry. You can go back to the last edited entry by clicking the button at the bottom\n\nThank you and good luck, scroll down further for more information on how to get started.\n\n\nOn the left side you will see a list of keys, each key has a corresponding line of text that you can translate.\n\nThe translated files can be found in the 'ReQorder Translator/translated' folder.\n\nThe source file is in English by default. You can choose to load a different source file if you wish. It is imperative however that the source file contains all the keys so it's probably best to use the supplied 'Language.properties' file.\n\nWhen you first begin, the target file for your language will probably not exist. The target file is the file that will contain the translations that you make. You can load an existing target file in another language and browse the keys in order to get an idea of how the program works.\n\nA new target file is created automatically when you start the application for the first time or if no previous session could be found. You can create a new target file or load one at any time. \n\nYour session is automatically saved while you are working and will automatically load when you restart the application. However, it is probably a good idea to save the target file before closing this application. \n\nClick 'Save target file' and enter a filename, it will automatically be given the 'properties' extension. It is probably best to name the file after the language that it is translated to.\n");
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setMargin(new java.awt.Insets(15, 25, 15, 25));
        helpScrollPane.setViewportView(jTextArea1);

        helpDialog.getContentPane().add(helpScrollPane, java.awt.BorderLayout.CENTER);

        continueButton.setText("Continue");
        continueButton.setPreferredSize(new java.awt.Dimension(77, 50));
        continueButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                continueButtonActionPerformed(evt);
            }
        });
        helpDialog.getContentPane().add(continueButton, java.awt.BorderLayout.PAGE_END);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ReQorder Translating Utility");
        setMinimumSize(new java.awt.Dimension(1024, 768));
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                formWindowClosing(evt);
            }
        });

        jSplitPane1.setDividerLocation(150);

        jScrollPane3.setMinimumSize(new java.awt.Dimension(200, 16));
        jScrollPane3.setPreferredSize(new java.awt.Dimension(200, 130));

        jScrollPane3.setViewportView(keyList);

        jSplitPane1.setLeftComponent(jScrollPane3);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

        sourceTextArea.setEditable(false);
        sourceTextArea.setColumns(20);
        sourceTextArea.setLineWrap(true);
        sourceTextArea.setRows(5);
        sourceTextArea.setWrapStyleWord(true);
        jScrollPane1.setViewportView(sourceTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 125;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 10);
        jPanel1.add(jScrollPane1, gridBagConstraints);

        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

        targetTextArea.setColumns(20);
        targetTextArea.setLineWrap(true);
        targetTextArea.setRows(5);
        targetTextArea.setWrapStyleWord(true);
        targetTextArea.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyPressed(java.awt.event.KeyEvent evt)
            {
                targetTextAreaKeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                targetTextAreaKeyReleased(evt);
            }
        });
        jScrollPane2.setViewportView(targetTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 125;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(jScrollPane2, gridBagConstraints);

        loadSourceFileButton.setText("Load source file");
        loadSourceFileButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                loadSourceFileButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 0, 0);
        jPanel1.add(loadSourceFileButton, gridBagConstraints);

        nextButton.setText("Next (PgDn)");
        nextButton.setMaximumSize(new java.awt.Dimension(140, 24));
        nextButton.setMinimumSize(new java.awt.Dimension(140, 24));
        nextButton.setPreferredSize(new java.awt.Dimension(140, 24));
        nextButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                nextButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(nextButton, gridBagConstraints);

        nextEmptyButton.setText("Next empty");
        nextEmptyButton.setPreferredSize(new java.awt.Dimension(111, 22));
        nextEmptyButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                nextEmptyButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(nextEmptyButton, gridBagConstraints);

        copyButton.setText("Copy to clipboard");
        copyButton.setMaximumSize(new java.awt.Dimension(126, 24));
        copyButton.setMinimumSize(new java.awt.Dimension(126, 24));
        copyButton.setPreferredSize(new java.awt.Dimension(126, 24));
        copyButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                copyButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(copyButton, gridBagConstraints);

        previousButton.setText("Previous (PgUp)");
        previousButton.setMaximumSize(new java.awt.Dimension(140, 24));
        previousButton.setMinimumSize(new java.awt.Dimension(140, 24));
        previousButton.setPreferredSize(new java.awt.Dimension(140, 24));
        previousButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                previousButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(previousButton, gridBagConstraints);

        previousEmptyButton.setText("Previous empty");
        previousEmptyButton.setMaximumSize(new java.awt.Dimension(114, 24));
        previousEmptyButton.setMinimumSize(new java.awt.Dimension(114, 24));
        previousEmptyButton.setPreferredSize(new java.awt.Dimension(114, 24));
        previousEmptyButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                previousEmptyButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(previousEmptyButton, gridBagConstraints);

        loadTargetFileButton.setText("Load target file");
        loadTargetFileButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                loadTargetFileButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 0, 0);
        jPanel1.add(loadTargetFileButton, gridBagConstraints);

        newFileButton.setText("New target file");
        newFileButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                newFileButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 0, 0);
        jPanel1.add(newFileButton, gridBagConstraints);

        pasteButton.setText("Paste");
        pasteButton.setPreferredSize(new java.awt.Dimension(126, 22));
        pasteButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                pasteButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(pasteButton, gridBagConstraints);

        statusLabel.setText("Status");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 20, 5, 0);
        jPanel1.add(statusLabel, gridBagConstraints);

        prevWarningButton.setText("Previous warning");
        prevWarningButton.setMaximumSize(new java.awt.Dimension(120, 24));
        prevWarningButton.setMinimumSize(new java.awt.Dimension(120, 24));
        prevWarningButton.setPreferredSize(new java.awt.Dimension(120, 24));
        prevWarningButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                prevWarningButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(prevWarningButton, gridBagConstraints);

        nextWarningButton.setText("Next warning");
        nextWarningButton.setMaximumSize(new java.awt.Dimension(120, 24));
        nextWarningButton.setMinimumSize(new java.awt.Dimension(120, 24));
        nextWarningButton.setPreferredSize(new java.awt.Dimension(120, 24));
        nextWarningButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                nextWarningButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        jPanel1.add(nextWarningButton, gridBagConstraints);

        warningLabel.setText("Warning");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 20, 10, 0);
        jPanel1.add(warningLabel, gridBagConstraints);

        saveFileButton.setText("Save target file");
        saveFileButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveFileButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 0, 0);
        jPanel1.add(saveFileButton, gridBagConstraints);

        backButton.setText("Back to last edited line");
        backButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                backButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 0);
        jPanel1.add(backButton, gridBagConstraints);

        helpButton.setText("Help");
        helpButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                helpButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 0);
        jPanel1.add(helpButton, gridBagConstraints);

        jSplitPane1.setRightComponent(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 971, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 663, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void loadSourceFileButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadSourceFileButtonActionPerformed
    {//GEN-HEADEREND:event_loadSourceFileButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/translated");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("properties files (*.properties)", "properties");
        //        jfc.setSelectedFile(new File("properties.mv.db")); //show preferred filename in filechooser
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {            
            File selectedFile = jfc.getSelectedFile();   
            m_SourceFile = selectedFile;
            loadSourceFile(selectedFile);            
        }
    }//GEN-LAST:event_loadSourceFileButtonActionPerformed

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nextButtonActionPerformed
    {//GEN-HEADEREND:event_nextButtonActionPerformed
        int selected = keyList.getSelectedIndex();        
        int nextIndex = selected + 1 > keyListModel.getSize() - 1 ? 0 : selected + 1;
        keyChangedWithButton(nextIndex);
    }//GEN-LAST:event_nextButtonActionPerformed

    private void previousButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_previousButtonActionPerformed
    {//GEN-HEADEREND:event_previousButtonActionPerformed
         int selected = keyList.getSelectedIndex();        
        int nextIndex = selected - 1 < 0 ? keyListModel.getSize() - 1 : selected - 1;
        keyChangedWithButton(nextIndex);
    }//GEN-LAST:event_previousButtonActionPerformed

    private void nextEmptyButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nextEmptyButtonActionPerformed
    {//GEN-HEADEREND:event_nextEmptyButtonActionPerformed
         if(translations == null || translations.length == 0)
        {
            JOptionPane.showMessageDialog(this, "You need to load or create a target file before you can start editing");
            return;
        }
        
        int next = keyList.getSelectedIndex() + 1;  //start checking at next index
        next = next == -1 ? 0 : next;
        next = next >= keyListModel.size() ? keyListModel.getSize() - 1 : next;
        
        for(int i = next; i < translations.length; i++)
        {
            String translation = translations[i];
            if(translation.isBlank() || translation.isEmpty())
            {
                keyChangedWithButton(i);
                break;
            }
        }
    }//GEN-LAST:event_nextEmptyButtonActionPerformed

    private void copyButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_copyButtonActionPerformed
    {//GEN-HEADEREND:event_copyButtonActionPerformed
        if(keyList.getSelectedIndex() >= 0)
        {
            String original = originals.get(keyList.getSelectedIndex());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(original);
            clipboard.setContents(selection, selection);
            
            SwingUtilities.invokeLater(() ->
            {
                sourceTextArea.requestFocusInWindow();
                sourceTextArea.selectAll();
            });                    
        }
        
    }//GEN-LAST:event_copyButtonActionPerformed

    private void loadTargetFileButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadTargetFileButtonActionPerformed
    {//GEN-HEADEREND:event_loadTargetFileButtonActionPerformed
        if(keys == null || keys.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "You must load a source file first");
            return;
        }
        
        if(JOptionPane.showConfirmDialog(this, "Load a target file? All unsaved changes will be lost", "Confirm", 
                JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
            return;
        
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/translated");;
        int returnValue = jfc.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {
            
            File selectedFile = jfc.getSelectedFile();   
            loadTargetFile(selectedFile);            
        }
    }//GEN-LAST:event_loadTargetFileButtonActionPerformed

    private void newFileButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_newFileButtonActionPerformed
    {//GEN-HEADEREND:event_newFileButtonActionPerformed
        if(JOptionPane.showConfirmDialog(this, "Create new target file? Any unsaved changes will be lost."
                                                                        ,"Confirm",JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
        {
            //reload source file to re-write the lineExport list
            loadSourceFile(m_SourceFile);
            createNewTargetFile();
        }
    }//GEN-LAST:event_newFileButtonActionPerformed

    private void previousEmptyButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_previousEmptyButtonActionPerformed
    {//GEN-HEADEREND:event_previousEmptyButtonActionPerformed
        if(translations == null || translations.length == 0)
        {
            JOptionPane.showMessageDialog(this, "You need to load or create a target file before you can start editing");
            return;
        }
        
        int previous = keyList.getSelectedIndex() - 1;  //start checking at next index
        previous = previous == -1 ? 0 : previous;
        
        for(int i = previous; i >= 0; i--)
        {
            String translation = translations[i];
            if(translation.isBlank() || translation.isEmpty())
            {
                keyChangedWithButton(i);
                break;
            }
        }
    }//GEN-LAST:event_previousEmptyButtonActionPerformed

    private void targetTextAreaKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_targetTextAreaKeyReleased
    {//GEN-HEADEREND:event_targetTextAreaKeyReleased
       
        if(evt.getKeyCode() == KeyEvent.VK_ENTER)
        {
            targetTextArea.setText("");
            return;
        }
        
        saveTranslation(keyList.getSelectedIndex(), targetTextArea.getText());            

        if(evt.getKeyCode() == 33)//pgup
            previousButtonActionPerformed(null);

        if(evt.getKeyCode() == 34)//pgdwn
            nextButtonActionPerformed(null);            
        
    }//GEN-LAST:event_targetTextAreaKeyReleased

    private void pasteButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_pasteButtonActionPerformed
    {//GEN-HEADEREND:event_pasteButtonActionPerformed
         if(translations == null || translations.length == 0)
        {
            JOptionPane.showMessageDialog(this, "You need to load or create a target file before you can start editing");
            return;
        }     
        
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable t = clipboard.getContents(this);
        if (t == null)
            return;
        try
        {
            targetTextArea.setText((String) t.getTransferData(DataFlavor.stringFlavor));
            translations[keyList.getSelectedIndex()] = targetTextArea.getText();
            targetTextArea.requestFocus();
        }
        catch (UnsupportedFlavorException | IOException e)
        {
            e.printStackTrace();
        }
    }//GEN-LAST:event_pasteButtonActionPerformed

    private void prevWarningButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_prevWarningButtonActionPerformed
    {//GEN-HEADEREND:event_prevWarningButtonActionPerformed
        if(warnings == null || warnings.isEmpty())
            return;
        
        currentWarningIndex = currentWarningIndex > 0 ? currentWarningIndex - 1 : warnings.size() -1;
        keyChangedWithButton(warnings.get(currentWarningIndex).index);
    }//GEN-LAST:event_prevWarningButtonActionPerformed

    private void nextWarningButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nextWarningButtonActionPerformed
    {//GEN-HEADEREND:event_nextWarningButtonActionPerformed
         if(warnings == null || warnings.isEmpty())
            return;
          
        currentWarningIndex = currentWarningIndex < warnings.size() - 1 ? currentWarningIndex + 1 : 0;
        keyChangedWithButton(warnings.get(currentWarningIndex).index);
    }//GEN-LAST:event_nextWarningButtonActionPerformed

    private void saveFileButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveFileButtonActionPerformed
    {//GEN-HEADEREND:event_saveFileButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/translated");
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter("properties files (*.properties)", "properties");
        //        jfc.setSelectedFile(new File("properties.mv.db")); //show preferred filename in filechooser
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showSaveDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {            
            File selectedFile = jfc.getSelectedFile();         

            if(selectedFile.exists())
            {
                if(JOptionPane.showConfirmDialog(this, 
                        "File " + selectedFile.getName() + " already exists. Choose Yes to overwrite, No to cancel","Overwrite existing file?",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
                {                
                    return;
                }
            }
            
            PrintWriter out;
            try(OutputStream os = new FileOutputStream(selectedFile.getPath()))
            {
                 out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
                
                for(String line : linesExport)
                {                             
                    out.println(line);                
                }    
                out.close();
                
                JOptionPane.showMessageDialog(this, "File was saved:\n\n" + selectedFile.getPath());
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }   
        }
            
    }//GEN-LAST:event_saveFileButtonActionPerformed

    private void backButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_backButtonActionPerformed
    {//GEN-HEADEREND:event_backButtonActionPerformed
        keyChangedWithButton(lastEditedIndex);
    }//GEN-LAST:event_backButtonActionPerformed

    private void continueButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_continueButtonActionPerformed
    {//GEN-HEADEREND:event_continueButtonActionPerformed
        helpDialog.setVisible(false);
    }//GEN-LAST:event_continueButtonActionPerformed

    private void helpButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_helpButtonActionPerformed
    {//GEN-HEADEREND:event_helpButtonActionPerformed
        helpDialog.setVisible(true);
    }//GEN-LAST:event_helpButtonActionPerformed

    private void targetTextAreaKeyPressed(java.awt.event.KeyEvent evt)//GEN-FIRST:event_targetTextAreaKeyPressed
    {//GEN-HEADEREND:event_targetTextAreaKeyPressed
          if(evt.getKeyCode() == KeyEvent.VK_ENTER)
          {
            saveTranslation(keyList.getSelectedIndex(), targetTextArea.getText());
            nextEmptyButtonActionPerformed(null);              
          }
    }//GEN-LAST:event_targetTextAreaKeyPressed

    private void formWindowClosing(java.awt.event.WindowEvent evt)//GEN-FIRST:event_formWindowClosing
    {//GEN-HEADEREND:event_formWindowClosing
        if(JOptionPane.showConfirmDialog(this, "Save target file?", "Save file", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
            return;
        
        saveFileButtonActionPerformed(null);
    }//GEN-LAST:event_formWindowClosing

    /**
     * @param args the command line arguments
     */
    public static void main(String args[])
    {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.htmlClose 
         */
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if ("Nimbus".equals(info.getName()))
                {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        }
        catch (ClassNotFoundException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        catch (InstantiationException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        catch (IllegalAccessException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        catch (javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                new GUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton backButton;
    private javax.swing.JButton continueButton;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton helpButton;
    private javax.swing.JDialog helpDialog;
    private javax.swing.JScrollPane helpScrollPane;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JList<String> keyList;
    private javax.swing.JButton loadSourceFileButton;
    private javax.swing.JButton loadTargetFileButton;
    private javax.swing.JButton newFileButton;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton nextEmptyButton;
    private javax.swing.JButton nextWarningButton;
    private javax.swing.JButton pasteButton;
    private javax.swing.JButton prevWarningButton;
    private javax.swing.JButton previousButton;
    private javax.swing.JButton previousEmptyButton;
    private javax.swing.JButton saveFileButton;
    private javax.swing.JTextArea sourceTextArea;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JTextArea targetTextArea;
    private javax.swing.JLabel warningLabel;
    // End of variables declaration//GEN-END:variables

    
}

