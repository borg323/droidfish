/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2014  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.petero.droidfish.engine;

import android.os.Environment;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Leela engine running as process, started from assets resource. */
public class InternalLeela extends ExternalEngine {

    public InternalLeela(Report report, String workDir) {
        super("", workDir, report);
    }

    @Override
    protected File getOptionsFile() {
        File extDir = Environment.getExternalStorageDirectory();
        return new File(extDir, "/DroidFish/uci/leela.ini");
    }

    private long readCheckSum(File f) {
        try (InputStream is = new FileInputStream(f);
             DataInputStream dis = new DataInputStream(is)) {
            return dis.readLong();
        } catch (IOException e) {
            return 0;
        }
    }

    private void writeCheckSum(File f, long checkSum) {
        try (OutputStream os = new FileOutputStream(f);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.writeLong(checkSum);
        } catch (IOException ignore) {
        }
    }

    private long computeAssetsCheckSum(String sfExe) {

        try (InputStream is = context.getAssets().open(sfExe)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len <= 0)
                    break;
                md.update(buf, 0, len);
            }
            byte[] digest = md.digest(new byte[]{0});
            long ret = 0;
            for (int i = 0; i < 8; i++) {
                ret ^= ((long)digest[i]) << (i * 8);
            }
            return ret;
        } catch (IOException e) {
            return -1;
        } catch (NoSuchAlgorithmException e) {
            return -1;
        }
    }

    @Override
    protected String copyFile(File from, File exeDir) throws IOException {
        File to = new File(exeDir, "engine.exe");
        final String leelaExe = EngineUtil.internalLeelaName();

        // The checksum test is to avoid writing to /data unless necessary,
        // on the assumption that it will reduce memory wear.
        long oldCSum = readCheckSum(new File(internalSFPath()));
        long newCSum = computeAssetsCheckSum(leelaExe);
        if (oldCSum == newCSum)
            return to.getAbsolutePath();

        // Copy engine
        if (to.exists())
            to.delete();
        to.createNewFile();

        try (InputStream is = context.getAssets().open(leelaExe);
             OutputStream os = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len <= 0)
                    break;
                os.write(buf, 0, len);
            }
        }

        writeCheckSum(new File(internalSFPath()), newCSum);
        final String exePath = to.getAbsolutePath();

        // Check if weights file changed
        final String networkPath = "networks/";
        final String weightsFile = "weights";

        oldCSum = readCheckSum(new File(internalSWPath()));
        newCSum = computeAssetsCheckSum(networkPath + weightsFile);
        if (oldCSum == newCSum)
            return exePath;

        // Copy weights file
        File networksDir = new File(exeDir, networkPath);
        if (!networksDir.exists())
            networksDir.mkdir();
        to = new File(networksDir, weightsFile);
        if (to.exists())
            to.delete();
        to.createNewFile();

        try (InputStream is = context.getAssets().open(networkPath + weightsFile);
             OutputStream os = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len <= 0)
                    break;
                os.write(buf, 0, len);
            }
        }

        writeCheckSum(new File(internalSWPath()), newCSum);

        return exePath;
    }
}
