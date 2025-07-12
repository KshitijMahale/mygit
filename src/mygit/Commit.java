package mygit;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static mygit.Utils.*;

class Commit implements Serializable {
    private static final int COMMIT_NAME_LENGTH = UID_LENGTH - 2;
    private final String message;
    private static final String PATTERN = "EEE MMM dd HH:mm:ss yyyy Z";
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(PATTERN);
    private final String time;
    private final long timeForComparison;
    private final String parent1;
    private HashSet<String> files = new HashSet<>();
    private String sha;

    private Commit(String msg, Date date, String parent1) {
        this.message = msg;
        this.time = SIMPLE_DATE_FORMAT.format(date);
        this.timeForComparison = date.getTime();
        this.parent1 = parent1;
    }

    private static Commit getCommit(String msg, String parent) {
        return new Commit(msg, new Date(), parent);
    }

    String getID() {
        return sha;
    }

    String getMessage() {
        return message;
    }

    HashSet<String> getCommittedFiles() {
        HashSet<String> returnList = new HashSet<>();
        for (String file : files) {
            returnList.add(file.substring(UID_LENGTH));
        }
        return returnList;
    }

    static Boolean isCommit(String fileName) {
        return fileName.length() == COMMIT_NAME_LENGTH;
    }

    Boolean containsFileName(String fileName) {
        return getCommittedFiles().contains(fileName);
    }

    Boolean containsExactFile(File file) {
        String fileName = getFileID(file) + file.getName();
        return files.contains(fileName);
    }


    String getFullFileName(String fileName) {
        for (String fullFileName : files) {
            if (fullFileName.substring(UID_LENGTH).equals(fileName)) {
                return fullFileName;
            }
        }
        return null;
    }

    // Finds a commit by ID
    static Commit getCommitFromString(String commit) {
        if (commit.length() > 2) {
            String dirString = commit.substring(0, 2);
            File dir = join(Repository.OBJECTS_DIR, dirString);
            List<String> dirFiles = plainFilenamesIn(dir);
            if (dirFiles != null) {
                String commitStr = commit.substring(2);
                for (String file : dirFiles) {
                    String fileSubstring = file.substring(0, commitStr.length());
                    if (Commit.isCommit(file) && fileSubstring.equals(commitStr)) {
                        return readObject(join(dir, file), Commit.class);
                    }
                }
            }
        }
        System.out.println("No commit with that id exists.");
        System.exit(0);
        return null;
    }

    // generates SHA1 id for a file based on its contents
    static String getFileID(File file) {
        String s = readContentsAsString(file);
        return sha1(serialize(s));
    }

    // creates initial commit with timestamp 0
    static String firstCommit() {
        Commit c = new Commit("initial commit", new Date(0), null);
        c.saveCommitment();
        return c.getID();
    }

    private void saveCommitment() {
        sha = sha1(serialize(this));
        File file = join(Repository.OBJECTS_DIR, sha.substring(0, 2), sha.substring(2));
        writeObject(file, this);
    }

    static String makeCommitment(String msg) {
        Commit parent = Main.repo.getLatestCommit();
        Commit child = getCommit(msg, parent.getID());
        child.files = new HashSet<>(parent.files);
        return makeCommitmentHelper(child);
    }

    // Helper for creating new commits
    private static String makeCommitmentHelper(Commit c) {
        List<String> filesInStagingDir = plainFilenamesIn(Repository.STAGING_DIR);
        if (filesInStagingDir != null) {
            for (String file : plainFilenamesIn(Repository.STAGING_DIR)) {
                if (c.containsFileName(file)) {
                    c.removeFileFromCommit(file);
                }
                c.addFileToCommit(file);
            }
        }
        for (String file : Main.repo.rmStage) {
            c.removeFileFromCommit(file);
        }
        c.saveCommitment();
        return c.sha;
    }

    // staging area -> commit
    private void addFileToCommit(String fileString) {
        File file = join(Repository.STAGING_DIR, fileString);
        String fullFileName = getFileID(file) + fileString;
        files.add(fullFileName);
        saveFileForCommit(file.toPath(), fullFileName);
    }

    // staged file -> objects directory
    private static void saveFileForCommit(Path file, String fileName) {
        Path destination = join(Repository.OBJECTS_DIR,
                fileName.substring(0, 2), fileName.substring(2)).toPath();
        try {
            Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Removes file from tracked files
    private void removeFileFromCommit(String fileToRemove) {
        for (String file : files) {
            if (file.substring(UID_LENGTH).equals(fileToRemove)) {
                files.remove(file);
                return;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("===\ncommit ").append(sha).append("\n");
        str.append("Date: ").append(time).append("\n").append(message).append("\n");
        return str.toString();
    }

    void log() {
        System.out.println(this);
        Commit c = this;
        while (c.parent1 != null) {
            c = getCommitFromString(c.parent1);
            System.out.println(c);
        }
    }

    static ArrayList<Commit> getAllCommits() {
        ArrayList<Commit> returnFiles = new ArrayList<>();
        Pattern pattern = Pattern.compile("commit (.+)");
        try (Stream<String> lines = Files.lines(Repository.LOG_FILE.toPath())) {
            lines.map(pattern::matcher)
                    .filter(Matcher::find)
                    .forEach(mr -> returnFiles.add(getCommitFromString(mr.group(1))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return returnFiles;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Commit)) return false;
        Commit c = (Commit) o;
        return c.getID().equals(this.getID());
    }

    @Override
    public int hashCode() {
        return Integer.parseInt(sha.substring(0, 6), 16);
    }
}