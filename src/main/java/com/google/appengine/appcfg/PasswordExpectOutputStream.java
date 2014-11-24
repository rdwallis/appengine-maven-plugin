/*
 * Copyright (C) 2008-2010 pyx4j.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Created on Mar 13, 2010
 * @author vlads
 * @version $Id$
 */
package com.google.appengine.appcfg;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * Read writes only from ThreadGroup, from other threads just redirect to PrintStream.
 * 
 * @author vlads
 */
public class PasswordExpectOutputStream extends OutputStream {
    
    private final PrintStream out;

    private final ThreadGroup threads;

    private final Runnable onExpected;

    private final byte[] expect;
    
    private int match = 0;
    
    public PasswordExpectOutputStream(ThreadGroup threads, PrintStream out, Runnable onExpected) {
        this.threads = threads;
        this.out = out;
        this.onExpected = onExpected;
        try {
            this.expect = "Password for".getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isRedirectThread() {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        while ((threads != tg) && (tg != null)) {
            tg = tg.getParent();
        }
        return (threads == tg);
    }

    public void write(int b) throws IOException {
        if (isRedirectThread()) {
            expect((byte)(0xFF & b));
        }
        this.out.write(b);
    }
    
    private synchronized void expect(byte b)  {
        if (expect[match] == b) {
            match ++;
            if (match == expect.length) {
                match = 0;
                Thread t = new Thread(onExpected, "EnterPasswordThread");
                t.setDaemon(true);
                t.start();
            }
        } else {
            match = 0;
        }
    }
    

}
