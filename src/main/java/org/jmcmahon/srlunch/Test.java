/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jmcmahon.srlunch;

/**
 *
 * @author Joe
 */
public class Test {
    public static void main(String args[]) {
        SRLunchSpeechlet test = new SRLunchSpeechlet();
        System.out.println(test.getJsonMenuItemsFromSage("2015-12-28"));
        System.exit(0);
    }
}
