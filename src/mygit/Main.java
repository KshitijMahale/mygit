package mygit;

public class Main {
    // ensure the number of arguments are correct
    private static void paramLenCheck(String[] args, int n) {
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }

    static Repository repo = null;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        if (firstArg.equals("init")) {
            paramLenCheck(args, 1);
            new Repository();
        } else if (firstArg.equals("diff")) {
            paramLenCheck(args, 3);
            new Repository().diff(args[1], args[2]);
        } else if (!Repository.inRepo()) {
            System.out.println("Not in an initialized mygit directory.");
        } else {
            repo = Repository.loadHead();
            switch (firstArg) {
                case "add":
                    paramLenCheck(args, 2);
                    repo.add(args[1]);
                    break;
                case "commit":
                    paramLenCheck(args, 2);
                    repo.commit(args[1]);
                    break;
                case "rm":
                    paramLenCheck(args, 2);
                    repo.rm(args[1]);
                    break;
                case "log":
                    paramLenCheck(args, 1);
                    repo.log();
                    break;
                case "find":
                    paramLenCheck(args, 2);
                    Repository.find(args[1]);
                    break;
                case "status":
                    paramLenCheck(args, 1);
                    repo.status();
                    break;
                default:
                    System.out.println("No command with that name exists. ");
            }
        }
    }
}
