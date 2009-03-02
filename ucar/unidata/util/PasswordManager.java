/*
 * Copyright 1997-2006 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

// $Id: IdvAuthenticator.java,v 1.3 2007/05/09 21:59:26 dmurray Exp $

package ucar.unidata.util;



import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.CredentialsNotAvailableException;
import org.apache.commons.httpclient.auth.CredentialsProvider;
import org.apache.commons.httpclient.auth.RFC2617Scheme;



import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Misc;
import ucar.unidata.xml.XmlUtil;
import ucar.unidata.xml.XmlEncoder;

import java.io.File;
import java.awt.*;

import java.awt.event.*;

import java.net.*;

import java.util.Hashtable;
import java.util.Enumeration;

import javax.swing.*;


/**
 * This class is used to provide authentication of access controlled CDM datasets.
 *
 * @author IDV Development Team
 * @version $Id: PasswordManager.java,v 1.3 2007/05/09 21:59:26 dmurray Exp $
 */
public class PasswordManager implements CredentialsProvider {


    private static PasswordManager passwordManager;

    /** for the gui */
    private JLabel serverLabel;


    /** for the gui */
    private JDialog dialog;

    /** for the gui */
    private JTextField nameFld;

    /** for the gui */
    private JPasswordField passwdFld;

    /** for the gui */
    private JCheckBox saveCbx;

    /** for the gui */
    private boolean ok;

    /** Holds previously saved name/passwords */
    private Hashtable<String,UserInfo> table;

    /**
     * This keeps track of username/passwords we've been using during the current run.
     *   This allows us to know when one failed so we don't keep looping
     */
    private Hashtable currentlyUsedOnes = new Hashtable();

    private File stateDir;


    /**
     * constructor
     *
     */
    public PasswordManager(File stateDir) {
        this.stateDir = stateDir;
    }

    public static PasswordManager getGlobalPasswordManager() {
        return passwordManager;
    }

    public static void setGlobalPasswordManager(PasswordManager manager) {
        if(passwordManager!=null) {
            throw new IllegalArgumentException("Already have a password manager");
        }
        passwordManager = manager;
    }



    /**
     * Do the authentication
     *
     * @param scheme scheme
     * @param host host
     * @param port port
     * @param proxy proxy
     *
     * @return Null if the user presses cancel. Else return the credentials
     *
     * @throws CredentialsNotAvailableException On badness
     */
    public Credentials getCredentials(AuthScheme scheme, String host,
                                      int port, boolean proxy)
            throws CredentialsNotAvailableException {

        if (scheme == null) {
            throw new CredentialsNotAvailableException(
                "Null authentication scheme: ");
        }

        if ( !(scheme instanceof RFC2617Scheme)) {
            throw new CredentialsNotAvailableException(
                "Unsupported authentication scheme: "
                + scheme.getSchemeName());
        }


        String key = host + ":" + port + ":" + scheme.getRealm();
        //        System.err.println ("got auth call " + key);
        UserInfo userInfo = getUserNamePassword(key,"The server " + host + ":" + port +" requires a username/password");
        if(userInfo == null) return null;
        return new UsernamePasswordCredentials(userInfo.getUserId(), userInfo.getPassword());
    }



    public UserInfo getUserNamePassword(String key, String label) {
        UserInfo userInfo =  getTable().get(key);

        if (userInfo != null) {
            if (currentlyUsedOnes.get(userInfo) != null) {
                userInfo = null;
            }
        }

        if (userInfo == null) {
            if (dialog == null) {
                makeDialog();
            }
            serverLabel.setText(label);
            ok = false;
            dialog.pack();
            dialog.setVisible(true);
            if ( !ok) {
                return null;
            }
            userInfo = new UserInfo(key,
                                            nameFld.getText().trim(),
                                            new String(passwdFld.getPassword()).trim());
            if (saveCbx.isSelected()) {
                table.put(key, userInfo);
                writeTable();
           }
        }
        currentlyUsedOnes.put(userInfo, "");
        return userInfo ;
    }


    protected void writeTable()  {
        try {
            String xml = XmlEncoder.encodeObject(getTable());
            IOUtil.writeFile(IOUtil.joinDir(stateDir,"authentication.xml"), xml);
        } catch(Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    protected Hashtable<String,UserInfo> getTable() {
        if (table == null) {
            try {
                String xml = IOUtil.readContents(IOUtil.joinDir(stateDir,"authentication.xml"),(String)null);
                Hashtable tmp = null;
                if(xml!=null) {
                    tmp = (Hashtable) XmlEncoder.decodeXml(xml);
                }
                if (tmp == null) {
                    tmp = new Hashtable();
                }
                //                table = tmp;
                table = new Hashtable();

                //Convert to the new passwordinfo
                for (Enumeration keys = tmp.keys();
                     keys.hasMoreElements(); ) {
                    String key   = (String) keys.nextElement();
                    Object  value = tmp.get(key);
                    if(!(value instanceof UserInfo)) {
                        String[]pair = decode(value);
                        value = new UserInfo(key,pair[0],pair[1]);
                    }
                    table.put(key,(UserInfo)value);
                }
            } catch(Exception exc) {
                throw new RuntimeException(exc);
            }
        }
        return table;

    }





    /**
     * Decode the encoded username/password pair
     *
     * @param obj the encoded object
     *
     * @return the pair
     */
    private String[] decode(Object obj) {
        String s     = (String) obj;
        byte[] bytes = XmlUtil.decodeBase64(s);
        return (String[]) Misc.deserialize(bytes);
    }


    /**
     * Make the gui
     */
    private void makeDialog() {
        serverLabel =
            new JLabel("                                                ");
        nameFld   = new JTextField("", 10);
        passwdFld = new JPasswordField("", 10);
        saveCbx   = new JCheckBox("Save Password");
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                ok = true;
                dialog.dispose();
            }
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                ok = false;
                dialog.dispose();
            }
        });


        GuiUtils.tmpInsets = GuiUtils.INSETS_5;
        JPanel contents = GuiUtils.doLayout(new Component[] {
            GuiUtils.rLabel("Name:"),
            nameFld, GuiUtils.rLabel("Password:"), passwdFld,
            GuiUtils.filler(), saveCbx
        }, 2, GuiUtils.WT_N, GuiUtils.WT_N);


        contents = GuiUtils.topCenterBottom(
                                            serverLabel,
            contents, GuiUtils.wrap(GuiUtils.doLayout(new Component[] { okBtn,
                new JLabel(" "), cancelBtn }, 3, GuiUtils.WT_N,
                GuiUtils.WT_N)));
        dialog = GuiUtils.createDialog("Server Authentication", true);
        dialog.getContentPane().add(GuiUtils.inset(contents, 5));
        dialog.pack();
        dialog.setLocation(200, 200);

    }


}

