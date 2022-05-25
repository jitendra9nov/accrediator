/* (C) 2022 */
package com.accrediator;

import static com.accrediator.AccreditorProcessor.install;
import static com.accrediator.AccreditorProcessor.isValidUrl;
import static java.awt.FileDialog.LOAD;
import static java.awt.Font.BOLD;
import static java.awt.Font.DIALOG;
import static java.awt.Font.ITALIC;
import static java.awt.Font.PLAIN;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.LAST_LINE_END;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.NORTHWEST;
import static java.awt.GridBagConstraints.REMAINDER;
import static java.awt.Toolkit.getDefaultToolkit;
import static javax.imageio.ImageIO.read;
import static javax.swing.SwingUtilities.invokeLater;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.TitledBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccreditorPanel extends JFrame implements ActionListener {

	private static final long serialVersionUID = 869131315365379051L;

	private static final Logger LOGGER = LoggerFactory.getLogger(AccreditorPanel.class);
	private static AccreditorPanel instance;
	
	private static final String VERSION="Ver: 1.0";

	private boolean parent;

	private AccreditorPanel() {
		super("Accreditor");
		this.initUI();
	}

	private AccreditorPanel(final boolean parent) {
		this();
		this.parent = parent;
	}

	public static AccreditorPanel getInstance(final boolean parent) {
		if (null == instance) {
			instance = new AccreditorPanel(parent);
		}

		instance.setVisible(true);
		return instance;
	}

	public static void main(String[] args) {
		invokeLater(() -> getInstance(false)

		);
	}

	JButton certImportBtn = new JButton();
	JButton certImportFileBtn = new JButton();
	JButton certDeleteBtn = new JButton();
	JTextField certImportText;
	JTextField certImportFileText;
	JTextField certDeleteText;

	JPanel statusPanel;
	JProgressBar progressBar;
	JPanel progressPane;

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.certImportBtn) {
			this.importCertificate(false, this.certImportText, null);
		} else if (e.getSource() == this.certImportFileBtn) {
			FileDialog fileDialog = new FileDialog(this, "Choose a certificate file", LOAD);
			fileDialog.setDirectory(System.getProperty("user.home") + "/Downloads");
			fileDialog.setFile("*.cer;*.crt;*.cert;*.txt");
			fileDialog.setVisible(true);
			String fileName = fileDialog.getFile();
			String fileDir = fileDialog.getDirectory();

			if (null != fileName) {
				LOGGER.info("File Chosen {}{}", fileDir, fileName);
				this.importCertificate(false, this.certImportFileText, fileDir + fileName);
			}
		} else if (e.getSource() == this.certDeleteBtn) {
			this.importCertificate(true, this.certDeleteText, null);
		}
	}

	private void copyFromClipboard(final JTextField jTextField, String text) {
		try {
			String clipboardText = this.getClipboardContents();
			if (isNoneBlank(clipboardText) && isBlank(jTextField.getText())
					&& ("Delete".equals(text) || "File".equals(text) ? true : isValidUrl(clipboardText))) {
				jTextField.setText(clipboardText);
			}

		} catch (Exception e) {
			LOGGER.error("Instance", e);
		}
	}

	private void createTabbedPane() {
		JTabbedPane tabbedPane = new JTabbedPane();
		ImageIcon icon = null;

		JComponent importCert = this.makeTextPane("Import", this.certImportBtn, this.certImportText, true);
		tabbedPane.addTab("Import from URL", icon, importCert, "Import Missing Certificate");
		tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);

		JComponent importFileCert = this.makeTextPane("File", this.certImportFileBtn, this.certImportFileText, true);
		tabbedPane.addTab("Import from File", icon, importFileCert, "Import Missing Certificate");
		tabbedPane.setMnemonicAt(1, KeyEvent.VK_2);

		JComponent deleteCert = this.makeTextPane("Delete", this.certDeleteBtn, this.certDeleteText, true);
		tabbedPane.addTab("Delete Cert", icon, deleteCert, "Delete Existing Certificate");
		tabbedPane.setMnemonicAt(2, KeyEvent.VK_3);

		tabbedPane.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				switch (tabbedPane.getSelectedIndex()) {
				case 0:
					certImportText.grabFocus();
					copyFromClipboard(certImportText, "Import");
					break;
				case 1:
					certImportFileText.grabFocus();
					copyFromClipboard(certImportFileText, "File");
					break;
				case 2:
					certDeleteText.grabFocus();
					copyFromClipboard(certDeleteText, "Delete");
					break;
				default:
					break;
				}
			}

			@Override
			public void focusLost(FocusEvent e) {

			}
		});
		// Add tabbed pane o this panel
		this.add(tabbedPane, BorderLayout.CENTER);

		// The following line enables to use scrolling tabs.
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

	}

	private static Clipboard getSystemClipboard() {
		return getDefaultToolkit().getSystemClipboard();

	}

	private static String getClipboardContents() throws IOException, UnsupportedFlavorException {
		Clipboard systemClipboard = getSystemClipboard();
		DataFlavor dataFlavor = DataFlavor.stringFlavor;

		if (systemClipboard.isDataFlavorAvailable(dataFlavor)) {
			Object text = systemClipboard.getData(dataFlavor);
			return (String) text;
		}
		return null;
	}

	private void importCertificate(final boolean isDel, final JTextField cerText, final String certFilePath) {
		String url = null;

		toggleButton(false);

		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws Exception {

				String url = null;

				try {
					url = cerText.getText();

					if (null != url) {
						url = url.trim();
						if (isEmpty(url)) {
							throw new IllegalArgumentException(
									"Please provide valid https URL (e.g. https://domain.com)"
											+ (isDel ? "OR Aliad Name" : ""));
						}
						install(url, isDel, certFilePath, null, null);
						cerText.setText("");
						cerText.grabFocus();
					}

				} catch (Exception e) {
					final String string = isDel ? "Delete " : "Import ";
					JOptionPane.showMessageDialog(null,
							(null != e.getMessage() ? e.getMessage() : "Unable to " + string),
							(isEmpty(url) ? "URL " : string) + "Error", JOptionPane.ERROR_MESSAGE, null);
					cerText.setText("");
					cerText.grabFocus();
					LOGGER.warn("Certificate Not Loaded", e);

				} finally {
					toggleButton(true);
				}
				return true;
			}

			@Override
			protected void done() {
				cerText.setText("");
				cerText.grabFocus();
				toggleButton(true);
			}

		};

		worker.execute();

	}

	private void initUI() {
		// setDefaultLookAndFeelDecorated(true);

		this.setIconImage(this.loadImage("/cert.png"));
		// this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		final String title = this.getTitle();

		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				final Object[] options = { "Yes", "No" };

				// Ask for confirmation before termination the program.
				final int option = JOptionPane.showOptionDialog(null,
						"Are you sure you want to close the '" + title + "' application?", "Close Confirmation",
						JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

				if (option == JOptionPane.YES_OPTION) {
					if (parent) {
						dispose();
					} else {
						System.exit(0);
					}
				}
			}

		});
		statusPaneWithTimer();

		this.certImportText = new JTextField(30) {

			private static final long serialVersionUID = 8045530435784631170L;

			@Override
			public JToolTip createToolTip() {
				JToolTip toolTip = super.createToolTip();
				toolTip.setBackground(Color.BLACK);
				toolTip.setForeground(new Color(255, 255, 51));
				toolTip.setFont(new Font(DIALOG, BOLD + ITALIC, 10));
				return toolTip;
			}
		};
		this.certImportFileText = new JTextField(30) {

			private static final long serialVersionUID = 6511391400814981438L;

			@Override
			public JToolTip createToolTip() {
				JToolTip toolTip = super.createToolTip();
				toolTip.setBackground(Color.BLACK);
				toolTip.setForeground(new Color(255, 255, 51));
				toolTip.setFont(new Font(DIALOG, BOLD + ITALIC, 10));
				return toolTip;
			}
		};
		this.certDeleteText = new JTextField(30) {

			private static final long serialVersionUID = -7616678902020779013L;

			@Override
			public JToolTip createToolTip() {
				JToolTip toolTip = super.createToolTip();
				toolTip.setBackground(Color.BLACK);
				toolTip.setForeground(new Color(255, 255, 51));
				toolTip.setFont(new Font(DIALOG, BOLD + ITALIC, 10));
				return toolTip;
			}
		};

		this.createTabbedPane();
		this.setResizable(false);
		this.setSize(450, 140);
		this.setLocationRelativeTo(null);
		this.setVisible(true);
	}

	private BufferedImage loadImage(String imageName) {
		try (InputStream res = this.getClass().getResourceAsStream(imageName);) {
			return read(res);
		} catch (Exception e) {
			LOGGER.error(imageName, e);
		}
		return null;
	}

	protected JComponent makeTextPane(String text, JButton jButton, JTextField jTextField, boolean urlOnly) {
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new GridBagLayout());
		GridBagConstraints gridBagConstraints = new GridBagConstraints();

		jTextField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				copyFromClipboard(jTextField, text);
				jTextField.setBackground(new Color(255, 255, 200));
				jTextField.setFont(new Font(DIALOG, BOLD, 12));
				jTextField.setToolTipText("Please provide valid https URL (e.g. https://domain.com)"
						+ (!urlOnly ? " OR Alias Name" : ""));
			}

			@Override
			public void focusLost(FocusEvent e) {
			}
		});

		jTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					jButton.doClick();
				}
			}
		});

		jTextField.addMouseListener(new MouseAdapter() {

			final int defaultTimeout = ToolTipManager.sharedInstance().getInitialDelay();

			final int defaultDismiss = ToolTipManager.sharedInstance().getDismissDelay();

			@Override
			public void mouseEntered(MouseEvent e) {
				ToolTipManager.sharedInstance().setInitialDelay(0);
				ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				ToolTipManager.sharedInstance().setInitialDelay(this.defaultTimeout);
				ToolTipManager.sharedInstance().setDismissDelay(this.defaultDismiss);
			}
		});

		String fieldText = "Delete".equals(text) || "File".equals(text) ? "URL/Alias" : "URL:";
		JLabel lblUrl = new JLabel(fieldText);
		lblUrl.setFont(new Font(DIALOG, BOLD, 12));
		lblUrl.setLabelFor(jTextField);

		jButton.setText("File".equals(text) ? "Browse & Import" : text);
		jButton.setMargin(new Insets(1, 1, 1, 1));
		jButton.setFont(new Font(DIALOG, BOLD, 12));
		jButton.setToolTipText(text + " Certificate");
		jButton.addActionListener(this);

		KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK);
		InputMap inputMap = jButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = jButton.getActionMap();

		inputMap.put(keyStroke, keyStroke.toString());

		actionMap.put(keyStroke.toString(), new AbstractAction() {
			private static final long serialVersionUID = -5901274177358279548L;

			@Override
			public void actionPerformed(ActionEvent e) {
				jButton.doClick();
			}
		});

		JPanel importPanel = new JPanel();

		importPanel.setLayout(new GridBagLayout());

		GridBagConstraints bagConstraints = new GridBagConstraints();

		bagConstraints.anchor = NORTHWEST;
		bagConstraints.fill = HORIZONTAL;
		bagConstraints.insets = new Insets(1, 1, 1, 1);
		bagConstraints.ipadx = 1;
		bagConstraints.ipady = 1;

		bagConstraints.gridx = 0;
		bagConstraints.gridy = 0;
		importPanel.add(lblUrl, bagConstraints);

		bagConstraints.gridx = 1;
		bagConstraints.gridy = 0;
		bagConstraints.weightx = 25.0;
		bagConstraints.gridwidth = REMAINDER;
		importPanel.add(jTextField, bagConstraints);

		bagConstraints.gridx = 1;
		bagConstraints.gridy = 1;
		bagConstraints.fill = NONE;
		bagConstraints.anchor = LAST_LINE_END;
		importPanel.add(jButton, bagConstraints);

		importPanel.setBorder(new TitledBorder(null, "Details", TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION,
				new Font(DIALOG, PLAIN, 12), null));

		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.weighty = 1.0;
		gridBagConstraints.anchor = NORTHWEST;
		gridBagConstraints.fill = HORIZONTAL;
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 0;
		mainPanel.add(importPanel, gridBagConstraints);

		return mainPanel;

	}

	private void statusPaneWithTimer() {
		// create status ares
		statusPanel = new JPanel();
		statusPanel.setPreferredSize(new Dimension(this.getWidth(), 15));
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.LINE_AXIS));
		statusPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		// create Progress Area
		progressPane = new JPanel();
		progressPane.setPreferredSize(new Dimension(100, 15));
		progressPane.setLayout(new BoxLayout(progressPane, BoxLayout.LINE_AXIS));
		progressPane.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		// create Progress bar
		progressBar = new JProgressBar(0, 1000);
		progressBar.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		progressBar.setStringPainted(false);
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);

		progressPane.add(progressBar);
		progressPane.add(new JLabel(" "));
		
		JLabel time=new JLabel();
		
		Font font=new Font("Arial", BOLD, 9);
		time.setFont(font);
		this.statusPanel.add(time);
		
		Timer timer=new Timer(1000, new ActionListener() {
			SimpleDateFormat fmt=new SimpleDateFormat("HH:mm:ss");
			
			@Override
			public void actionPerformed(ActionEvent e) {
				String timeString=fmt.format(new Date());
				time.setText("  "+timeString+ "  "+VERSION+" ");
				
			}
		});
		timer.setInitialDelay(0);
		timer.start();
		
		statusPanel.add(progressPane);
		this.add(statusPanel,BorderLayout.PAGE_END);
		toggleButton(true);
		
		
	}

	private void toggleButton(boolean toggle) {
		progressPane.setVisible(!toggle);
		progressBar.setVisible(!toggle);
		this.repaint();
		this.requestFocus();
		

	}

}