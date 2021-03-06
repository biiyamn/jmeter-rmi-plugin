/*
 *
 *
 * See README in the source tree for more info
 *
 */
 
package com.jmibanez.tools.jmeter.gui;

import org.apache.jmeter.control.gui.LogicControllerGui;
import javax.swing.tree.TreeNode;
import org.apache.jmeter.gui.JMeterGUIComponent;
import java.util.Collection;
import javax.swing.JPopupMenu;
import org.apache.jmeter.testelement.TestElement;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import org.apache.jmeter.gui.UnsharedComponent;
import com.jmibanez.tools.jmeter.NativeRmiProxyController;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.Box;
import javax.swing.JButton;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.engine.util.ValueReplacer;
import org.apache.jmeter.functions.InvalidVariableException;
import javax.swing.JOptionPane;
import org.apache.jmeter.util.JMeterUtils;
import java.util.Arrays;
import org.apache.jmeter.gui.util.MenuFactory;
import javax.swing.JTextField;
import java.awt.event.KeyListener;
import javax.swing.JLabel;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import org.apache.jmeter.gui.util.HorizontalPanel;
import org.apache.jmeter.gui.util.JSyntaxTextArea;
import org.apache.jmeter.gui.util.JTextScrollPane;
import org.apache.jmeter.gui.util.VerticalPanel;

/**
 * Describe class NativeRmiProxyControllerGui here.
 *
 *
 * Created: Sat Nov 15 22:09:13 2008
 *
 * @author <a href="mailto:jm@jmibanez.com">JM Ibanez</a>
 * @version 1.0
 */
public class NativeRmiProxyControllerGui extends LogicControllerGui implements KeyListener, UnsharedComponent, ItemListener, ActionListener, JMeterGUIComponent {

    public static final long serialVersionUID = 800030L;

    private static final String TARGETNAME_FIELD = "targetRmiName";
    private static final String PROXYNAMINGPORT_FIELD = "proxyNamingPort";
    private static final String PROXYPORT_FIELD = "proxyPort";
    private static final String SAMPLERNAMEFORMAT_FIELD = "samplerNameFormat";
    private static final String BINDINGSCRIPT_FIELD = "bindingScript";

    private static final String START = "start";
    private static final String STOP = "stop";

    private NativeRmiProxyController model;

    private JTextField targetRmiName;
    private JTextField proxyNamingPort;
    private JTextField proxyPort;
    private JTextField samplerNameFormat;
    private JSyntaxTextArea bindingScript;
    private JTextScrollPane scroller;

    private JButton start;
    private JButton stop;


    public NativeRmiProxyControllerGui() {
        super();
        init();
    }



    // Implementation of java.awt.event.KeyListener

    /**
     * Describe <code>keyTyped</code> method here.
     *
     * @param keyEvent a <code>KeyEvent</code> value
     */
    public void keyTyped(KeyEvent keyEvent) {
    }

    /**
     * Describe <code>keyPressed</code> method here.
     *
     * @param keyEvent a <code>KeyEvent</code> value
     */
    public void keyPressed(KeyEvent keyEvent) {
    }

    /**
     * Describe <code>keyReleased</code> method here.
     *
     * @param keyEvent a <code>KeyEvent</code> value
     */
    public void keyReleased(KeyEvent e) {
        String fieldName = e.getComponent().getName();

        if (fieldName.equals(PROXYPORT_FIELD) || fieldName.equals(PROXYNAMINGPORT_FIELD)) {
            JTextField field = fieldName.equals(PROXYNAMINGPORT_FIELD) ? proxyNamingPort : proxyPort;

            try {
                Integer.parseInt(field.getText());
            } catch (NumberFormatException nfe) {
                int length = field.getText().length();
                if (length > 0) {
                    JOptionPane.showMessageDialog(this, "Only digits allowed", "Invalid data",
                            JOptionPane.WARNING_MESSAGE);
                    // Drop the last character:
                    field.setText(field.getText().substring(0, length-1));
                }
            }
        }
    }


    // Implementation of java.awt.event.ItemListener

    /**
     * Describe <code>itemStateChanged</code> method here.
     *
     * @param itemEvent an <code>ItemEvent</code> value
     */
    public final void itemStateChanged(final ItemEvent itemEvent) {

    }


    // Implementation of java.awt.event.ActionListener

    /**
     * Describe <code>actionPerformed</code> method here.
     *
     * @param actionEvent an <code>ActionEvent</code> value
     */
    public void actionPerformed(ActionEvent action) {
        String command = action.getActionCommand();

        if(command.equals(STOP)) {
            model.unbindProxy();
            stop.setEnabled(false);
            start.setEnabled(true);
        }
        else if(command.equals(START)) {
            ValueReplacer replacer = GuiPackage.getInstance().getReplacer();
            modifyTestElement(model);

            try {
                replacer.replaceValues(model);
                start.setEnabled(false);
                stop.setEnabled(true);

                model.bindProxy();
            }
            catch(InvalidVariableException e) {
                JOptionPane.showMessageDialog(this,
                                              JMeterUtils.getResString("invalid_variables"),
                                              "Error",
                                              JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    // Implementation of org.apache.jmeter.gui.JMeterGUIComponent

    @Override
    public Collection<String> getMenuCategories() {
        return Arrays.asList(new String[] { MenuFactory.NON_TEST_ELEMENTS });
    }

    /**
     * Describe <code>setNode</code> method here.
     *
     * @param treeNode a <code>TreeNode</code> value
     */
    public void setNode(final TreeNode treeNode) {

    }

    /**
     * Describe <code>getStaticLabel</code> method here.
     *
     * @return a <code>String</code> value
     */
    public String getStaticLabel() {
        return "RMI Proxy";
    }

    /**
     * Describe <code>getLabelResource</code> method here.
     *
     * @return a <code>String</code> value
     */
    public final String getLabelResource() {
        return "rmi_proxy";
    }

    /**
     * Describe <code>getDocAnchor</code> method here.
     *
     * @return a <code>String</code> value
     */
    public final String getDocAnchor() {
        return null;
    }

    /**
     * Describe <code>createTestElement</code> method here.
     *
     * @return a <code>TestElement</code> value
     */
    public TestElement createTestElement() {
        NativeRmiProxyController ctrl = new NativeRmiProxyController();
        modifyTestElement(ctrl);
        return ctrl;
    }

    /**
     * Describe <code>modifyTestElement</code> method here.
     *
     * @param testElement a <code>TestElement</code> value
     */
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);

        if(element instanceof NativeRmiProxyController) {
            model = (NativeRmiProxyController) element;
            model.setTargetRmiName(targetRmiName.getText());
            model.setProxyNamingPort(proxyNamingPort.getText());
            model.setProxyPort(proxyPort.getText());
            model.setSamplerNameFormat(samplerNameFormat.getText());
            model.setBindingScript(bindingScript.getText());
        }
    }

    /**
     * Describe <code>configure</code> method here.
     *
     * @param testElement a <code>TestElement</code> value
     */
    public void configure(TestElement el) {
        super.configure(el);
        model = (NativeRmiProxyController) el;
        targetRmiName.setText(model.getTargetRmiName());
        proxyNamingPort.setText(Integer.toString(model.getProxyNamingPort()));
        proxyPort.setText(Integer.toString(model.getProxyPort()));
        samplerNameFormat.setText(model.getSamplerNameFormat());
        bindingScript.setText(model.getBindingScript());
    }


    /**
     * Describe <code>clearGui</code> method here.
     *
     */
    public void clearGui() {

    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        add(makeTitlePanel(), BorderLayout.NORTH);

        // Box config = Box.createVerticalBox();

        // mainPanel.add(config, BorderLayout.NORTH);

        targetRmiName = new JTextField("", 40);
        targetRmiName.setName(TARGETNAME_FIELD);
        //targetRmiName.addKeyListener(this);

        JLabel label = new JLabel("Target RMI name");
        label.setLabelFor(targetRmiName);

        proxyNamingPort = new JTextField("1100", 5);
        proxyNamingPort.setName(PROXYNAMINGPORT_FIELD);
        proxyNamingPort.addKeyListener(this);

        JLabel nPortLabel = new JLabel("Proxy Naming Port");
        nPortLabel.setLabelFor(proxyNamingPort);

        proxyPort = new JTextField("1101", 5);
        proxyPort.setName(PROXYPORT_FIELD);
        proxyPort.addKeyListener(this);

        JLabel portLabel = new JLabel("Proxy Port");
        portLabel.setLabelFor(proxyPort);

        samplerNameFormat = new JTextField("[%1$s] %2$d - %3$s:%4$s", 100);
        samplerNameFormat.setName(SAMPLERNAMEFORMAT_FIELD);

        JLabel samplerNameFormatLabel = new JLabel("Sampler Name Format [1: Target; 2: Call Index; 3: Method; 4: Args]");
        samplerNameFormatLabel.setLabelFor(samplerNameFormat);

        JPanel configBox = new HorizontalPanel();

        JPanel configLeftBox = new VerticalPanel();
        configLeftBox.setPreferredSize(new Dimension(400, 120));
        JPanel configRightBox = new VerticalPanel();
        configRightBox.setPreferredSize(new Dimension(0, 120));

        JPanel targetRmiPanel = new VerticalPanel();
        targetRmiPanel.add(label);
        targetRmiPanel.add(targetRmiName);
        configLeftBox.add(targetRmiPanel);

        JPanel samplerNameFormatPanel = new VerticalPanel();
        samplerNameFormatPanel.add(samplerNameFormatLabel);
        samplerNameFormatPanel.add(samplerNameFormat);
        configLeftBox.add(samplerNameFormatPanel);

        JPanel proxyNamingPortPanel = new VerticalPanel();
        proxyNamingPortPanel.add(nPortLabel);
        proxyNamingPortPanel.add(proxyNamingPort);
        configRightBox.add(proxyNamingPortPanel);

        JPanel proxyPortPanel = new VerticalPanel();
        proxyPortPanel.add(portLabel);
        proxyPortPanel.add(proxyPort);
        configRightBox.add(proxyPortPanel);

        configBox.add(configLeftBox);
        configBox.add(configRightBox);

        bindingScript = JSyntaxTextArea.getInstance(20, 20);
        scroller = JTextScrollPane.getInstance(bindingScript, true);
        bindingScript.discardAllEdits();

        JLabel bLabel = new JLabel("Binding Script");
        bLabel.setLabelFor(scroller);

        JPanel bindScriptPanel = new VerticalPanel();
        bindScriptPanel.add(bLabel);
        bindScriptPanel.add(scroller);

        JPanel configPanel = new VerticalPanel();
        configPanel.add(configBox, BorderLayout.NORTH);
        configPanel.add(bindScriptPanel, BorderLayout.CENTER);
        add(configPanel, BorderLayout.CENTER);

        start = new JButton("Start");
        start.addActionListener(this);
        start.setActionCommand(START);
        start.setEnabled(true);

        stop = new JButton("Stop");
        stop.addActionListener(this);
        stop.setActionCommand(STOP);
        stop.setEnabled(false);

        JPanel controlPanel = new JPanel();
        controlPanel.add(start);
        controlPanel.add(stop);

        add(controlPanel, BorderLayout.SOUTH);
    }

}
