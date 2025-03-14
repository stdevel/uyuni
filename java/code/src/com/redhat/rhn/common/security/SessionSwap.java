/*
 * Copyright (c) 2009--2014 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package com.redhat.rhn.common.security;

import com.redhat.rhn.common.conf.Config;
import com.redhat.rhn.common.conf.ConfigDefaults;
import com.redhat.rhn.common.util.SHA256Crypt;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * SessionSwap, a class to handle secure data manipulations in a way
 * consistent with SessionSwap from the perl codebase.  Effectively a
 * wrapper to make it a bit easier to exchange data with different
 * parts of our codebase that speak different languages.  A session
 * swap token is basically a tuple of a certain form that contains N
 * pieces of hex data and a signature that is based on a shared
 * secret.  Someday this should become a true HMAC, but for now, it is
 * an older algorithm.
 *
 */

public class SessionSwap {

    private static Logger log = LogManager.getLogger(SessionSwap.class);

    public static final char[] HEX_CHARS = {'0', '1', '2', '3',
                                             '4', '5', '6', '7',
                                             '8', '9', 'a', 'b',
                                             'c', 'd', 'e', 'f' };

    /** utility class, no public constructor  */
    private SessionSwap() {
    }

    /** given an array of strings, compute the hex session swap, which
     * contains both the original data and the 'signature'.  so the
     * resulting string is encapsulated and can be passed around as
     * 'signed' data.
     *
     * @param in an array of strings, all of which must be valud hex
     * @return String of the signature, in the form "D1:D2:D3xHEX"
     *         where D1... are the input data and HEX is the hex signature.
     */
    public static String encodeData(String[] in) {
        for (String sIn : in) {
            if (!StringUtils.containsOnly(sIn, HEX_CHARS)) {
                throw new IllegalArgumentException("encodeData input must be " +
                        "lowercase hex, but wasn't: " + sIn);
            }
        }

        String joined = StringUtils.join(in, ':');

        String[] components = new String[] { joined, generateSwapKey(joined) };

        return StringUtils.join(components, "x");
    }

    /**
     * simple wrapper around encodeData(String[]) for easier consumption
     * @see SessionSwap#encodeData(String[]) encodeData
     * @param in The data to encode
     * @return The reulting session swap string.
     */
    public static String encodeData(String in) {
        return encodeData(new String[] { in });
    }

    /** given a session swap string, this will crack it open and
     * return the data.
     * @param in The session swap to inspect.
     * @return The data extracted from the session swap
     * @throws SessionSwapTamperException if the data was
     *         tampered with, making it easy to use and trust
     */
    public static String[] extractData(String in) {
        String[] splitResults = StringUtils.split(in, 'x');
        String[] data = StringUtils.split(splitResults[0], ':');

        String recomputedDigest = encodeData(data);

        if (recomputedDigest.equals(in)) {
            return data;
        }
        throw new SessionSwapTamperException(in);
    }
    /**
     * compute the sha256sum of
     * key1:key2:(data):key3:key4.
     * @param data to compute
     * @return computed data
     */
    public static String generateSwapKey(String data) {
        Config c = Config.get();

        String swapKey = c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_1) +
                ":" +
                c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_2) +
                ":" +
                data +
                ":" +
                c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_3) +
                ":" +
                c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_4);
        return SHA256Crypt.sha256Hex(swapKey);
    }

    /**
     * Takes an array of strings and SHA1 hashes the 'joined' results.
     *
     * This is a port of the RHN::SessionSwap:rhn_hmac_data method.
     *
     * @param text array to SHA256 hash
     * @return String of hex chars
     */
    public static String rhnHmacData(List<String> text) {

        Config c = Config.get();
        StringBuilder swapKey = new StringBuilder(20);
        if (log.isDebugEnabled()) {
            for (String tmp : text) {
                log.debug("val : {}", tmp);
            }
        }
        swapKey.append(c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_4));
        swapKey.append(c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_3));
        swapKey.append(c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_2));
        swapKey.append(c.getString(ConfigDefaults.WEB_SESSION_SWAP_SECRET_1));

        String joinedText = StringUtils.join(text.iterator(), "\0");


        if (log.isDebugEnabled()) {
            log.debug("Data     : [{}]", joinedText);
            log.debug("Key      : [{}]", swapKey);
        }
        String retval = HMAC.sha256(joinedText, swapKey.toString());
        if (log.isDebugEnabled()) {
            log.debug("retval: {}", retval);
        }
        return retval;
    }

}
