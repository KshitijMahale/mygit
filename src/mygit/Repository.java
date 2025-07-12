package mygit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static mygit.Utils.*;

class Repository implements Serializable {
    private static final File CWD = new File(System.getProperty("user.dir"));
    private static final File mygit_DIR = join(CWD, ".mygit");
    static final File LOG_FILE = join(mygit_DIR, "log");
    static final File OBJECTS_DIR = join(mygit_DIR, "objects");
    static final File STAGING_DIR = join(mygit_DIR, "staging");
    static final String[] HEXADECIMAL_CHARS = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};

    private String latestCommit;
    private static final File REPO_FILE = join(mygit_DIR, "repo");
    HashSet<String> rmStage = new HashSet<>();

    // Initialize
    Repository() {
        if (!inRepo()) {
            createDirectories();
            latestCommit = Commit.firstCommit();
            initializeLog();
            saveRepo();
        } else if (!REPO_FILE.exists()) {
            latestCommit = Commit.firstCommit();
            initializeLog();
            saveRepo();
        }
    }

    Commit getLatestCommit() {
        return Commit.getCommitFromString(latestCommit);
    }

    static boolean inRepo() {
        return mygit_DIR.exists();
    }

    static Repository loadHead() {
        return readObject(REPO_FILE, Repository.class);
    }

    private static void createDirectories() {
        OBJECTS_DIR.mkdirs();
        for (String c1 : HEXADECIMAL_CHARS) {
            for (String c2 : HEXADECIMAL_CHARS) {
                join(OBJECTS_DIR, c1 + c2).mkdir();
            }
        }
        STAGING_DIR.mkdir();
    }

    private void initializeLog() {
        try (FileWriter writer = new FileWriter(LOG_FILE)) {
            writer.write(getLatestCommit().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Stage files
    void add(String file) {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        if (filesInCWD != null) {
            if (file.equals(".")) {
                // add all files in CWD
                for (String fileInCWD : filesInCWD) {
                    add(fileInCWD);
                }
                // stage deletions for missing tracked files
                Commit c = getLatestCommit();
                for (String tracked : c.getCommittedFiles()) {
                    if (!filesInCWD.contains(tracked) && !rmStage.contains(tracked)) {
                        rmStage.add(tracked);
                        saveRepo();
                    }
                }
                return;
            }

            if (filesInCWD.contains(file)) {
                if (rmStage.remove(file)) {
                    updateLog();
                    saveRepo();
                }
                Commit c = getLatestCommit();
                if (c.containsExactFile(join(CWD, file))) {
                    join(STAGING_DIR, file).delete();
                    return;
                }
                try {
                    Files.copy(join(CWD, file).toPath(),
                            join(STAGING_DIR, file).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Commit c = getLatestCommit();
                if (c.containsFileName(file) && !rmStage.contains(file)) {
                    rmStage.add(file);
                    saveRepo();
                } else {
                    System.out.println("File does not exist.");
                }
            }
        }
    }

    void commit(String msg) {
        List<String> filesInStagingDir = plainFilenamesIn(STAGING_DIR);
        if (filesInStagingDir != null) {
            if (filesInStagingDir.isEmpty() && rmStage.isEmpty()) {
                System.out.println("No changes added to the commit.");
            } else if (msg.isEmpty()) {
                System.out.println("Please enter a commit message.");
            } else {
                latestCommit = Commit.makeCommitment(msg);
                rmStage = new HashSet<>();
                updateLog();
                saveRepo();
            }
        }
    }

    private void updateLog() {
        String updatedLog = getLatestCommit().toString() + "\n" + readContentsAsString(LOG_FILE);
        try (FileWriter writer = new FileWriter(LOG_FILE, false)) {
            writer.write(updatedLog);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Remove file from tracking
    void rm(String file) {
        boolean check1 = join(STAGING_DIR, file).delete();
        boolean check2 = false;
        Commit c = getLatestCommit();
        if (c.containsFileName(file)) {
            rmStage.add(file);
            saveRepo();
            join(CWD, file).delete();
            check2 = true;
        }
        if (!(check1 || check2)) {
            System.out.println("No reason to remove the file.");
        }
    }

    void log() {
        getLatestCommit().log();
    }

    static void find(String msg) {
        boolean found = false;
        for (Commit c : Commit.getAllCommits()) {
            if (c.getMessage().equals(msg)) {
                System.out.println(c.getID());
                found = true;
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    // Show repository status
    void status() {
        statusStagedFiles();
        statusNotStaged();
        statusUntracked();
        System.out.println();
    }

    private void statusStagedFiles() {
        System.out.println("\n=== Staged Files ===");
        List<String> stagedFiles = plainFilenamesIn(STAGING_DIR);
        if (stagedFiles != null) {
            stagedFiles.forEach(System.out::println);
        }
    }

    private void statusNotStaged() {
        System.out.println("\n=== Modifications Not Staged For Commit ===");
        Commit c = getLatestCommit();
        List<String> filesInCWD = plainFilenamesIn(CWD);

        if (filesInCWD != null) {
            filesInCWD.forEach(fileString -> {
                File cwdFile = join(CWD, fileString);
                File stagingFile = join(STAGING_DIR, fileString);
                if ((c.containsFileName(fileString) && !c.containsExactFile(cwdFile) && !stagingFile.exists()) ||
                        (stagingFile.exists() && !Commit.getFileID(cwdFile).equals(Commit.getFileID(stagingFile)))) {
                    System.out.println(fileString + " (modified)");
                }
            });
        }

        List<String> filesInStaging = plainFilenamesIn(STAGING_DIR);
        if (filesInStaging != null) {
            filesInStaging.forEach(fileString -> {
                if (!join(CWD, fileString).exists()) {
                    System.out.println(fileString + " (deleted)");
                }
            });
        }

        c.getCommittedFiles().forEach(fileString -> {
            if (!rmStage.contains(fileString) &&
                    !join(CWD, fileString).exists() &&
                    !join(STAGING_DIR, fileString).exists()) {
                System.out.println(fileString + " (deleted)");
            }
        });
    }

    private void statusUntracked() {
        System.out.println("\n=== Untracked Files ===");
        getUntrackedFiles().forEach(System.out::println);
    }

    private List<String> getUntrackedFiles() {
        List<String> returnList = new ArrayList<>();
        Commit c = getLatestCommit();
        List<String> filesInCWD = plainFilenamesIn(CWD);
        List<String> stagedFiles = plainFilenamesIn(STAGING_DIR);

        if (filesInCWD != null) {
            filesInCWD.forEach(file -> {
                if (!(c.containsFileName(file) ||
                        (stagedFiles != null && stagedFiles.contains(file)))) {
                    returnList.add(file);
                }
            });
        }
        return returnList;
    }

    // Show differences between two commits
    public void diff(String commitId1, String commitId2) {
        Commit c1 = Commit.getCommitFromString(commitId1);
        Commit c2 = Commit.getCommitFromString(commitId2);
        HashSet<String> allFiles = new HashSet<>(c1.getCommittedFiles());
        allFiles.addAll(c2.getCommittedFiles());

        allFiles.forEach(file -> {
            boolean in1 = c1.containsFileName(file);
            boolean in2 = c2.containsFileName(file);

            if (!in1 && in2) {
                System.out.println("+ " + file);
            } else if (in1 && !in2) {
                System.out.println("- " + file);
            } else if (in1 && in2) {
                String f1 = c1.getFullFileName(file);
                String f2 = c2.getFullFileName(file);
                if (!f1.equals(f2)) {
                    System.out.println("modified: " + file);
                    printLineDiff(f1, f2);
                }
            }
        });
    }

    private void printLineDiff(String fileId1, String fileId2) {
        File file1 = getCommittedFile(fileId1);
        File file2 = getCommittedFile(fileId2);
        List<String> lines1 = List.of(readContentsAsString(file1).split("\\R"));
        List<String> lines2 = List.of(readContentsAsString(file2).split("\\R"));

        int i = 0, j = 0;
        while (i < lines1.size() || j < lines2.size()) {
            if (i < lines1.size() && (j >= lines2.size() || !lines1.get(i).equals(lines2.get(j)))) {
                System.out.println("- " + lines1.get(i++));
            } else if (j < lines2.size() && (i >= lines1.size() || !lines1.get(i).equals(lines2.get(j)))) {
                System.out.println("+ " + lines2.get(j++));
            } else {
                i++;
                j++;
            }
        }
    }

    private File getCommittedFile(String fullFileName) {
        String dirName = fullFileName.substring(0, 2);
        String fileName = fullFileName.substring(2);
        return join(OBJECTS_DIR, dirName, fileName);
    }

    private void saveRepo() {
        writeObject(REPO_FILE, this);
    }
}