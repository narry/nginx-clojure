/*
 * Copyright (c) 2008-2013, Matthias Mann
 * Copyright (C) 2014 Zhang,Yuexiang (xfeep)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2012, Enhanced Four
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'Enhanced Four' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nginx.clojure.wave;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.asm.ClassReader;
import nginx.clojure.asm.ClassVisitor;
import nginx.clojure.asm.ClassWriter;
import nginx.clojure.asm.util.CheckClassAdapter;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

/*
 * Created on Nov 21, 2010
 *
 * @author Riven
 * @author Matthias Mann
 */
public class JavaAgent {
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        MethodDatabase db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
        boolean checkArg = false;

        if(agentArguments != null) {
            for(char c : agentArguments.toCharArray()) {
                switch(c) {
                    case 'v':
                        db.setVerbose(true);
                        break;

                    case 'd':
                        db.setDebug(true);
                        break;

                    case 'm':
                        db.setAllowMonitors(true);
                        break;

                    case 'c':
                        checkArg = true;
                        break;

                    case 'b':
                        db.setAllowBlocking(true);
                        break;

                    default:
                        throw new IllegalStateException("Usage: vdmc (verbose, debug, allow monitors, check class)");
                }
            }
        }

        if (NginxClojureRT.getLog() != null) {
        	db.setLog(NginxClojureRT.getLog());
        }

        instrumentation.addTransformer(new CoroutineWaver(db, checkArg));
    }

    static byte[] instrumentClass(MethodDatabase db, byte[] data, boolean check) {
        ClassReader r = new ClassReader(data);
        ClassWriter cw = new DBClassWriter(db, r);
        ClassVisitor cv = check ? new CheckClassAdapter(cw) : cw;
        ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(db, r);
        InstrumentClass ic = new InstrumentClass(r.getClassName(), ce, cv, db, false);
        r.accept(ic, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    public static class CoroutineWaver implements ClassFileTransformer {
        private final MethodDatabase db;
        private final boolean check;

        public CoroutineWaver(MethodDatabase db, boolean check) {
            this.db = db;
            this.check = check;
            //load system configurations for method database
            try {
				MethodDatabaseUtil.load(db, "nginx/clojure/wave/coroutine-method-db.txt");
			} catch (IOException e) {
				db.error("file lost : nginx/clojure/wave/coroutine-method-db.txt", e);
			}
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if(db.shouldIgnore(className)) {
                return null;
            }

            db.debug("TRANSFORM: %s", className);

            try {
                return instrumentClass(db, classfileBuffer, check);
            } catch(Exception ex) {
                db.error("Unable to instrument:" + className, ex);
                return null;
            }
        }
    }
}
