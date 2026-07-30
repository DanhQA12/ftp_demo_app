package server.controller;

import server.model.FTPServerModel;
import server.view.FTPServerView;
import javax.swing.*;

public class FTPServerController {
    private final FTPServerModel model;
    private final FTPServerView view;

    public FTPServerController(FTPServerModel model, FTPServerView view) {
        this.model = model;
        this.view = view;

        this.view.getStartButton().addActionListener(e -> {
            this.view.getStartButton().setEnabled(false);
            this.view.getStopButton().setEnabled(true);
            this.model.startServer(
                    this.view::appendLog,
                    () -> SwingUtilities.invokeLater(() -> {
                        this.view.getStartButton().setEnabled(true);
                        this.view.getStopButton().setEnabled(false);
                    })
            );
        });

        this.view.getStopButton().addActionListener(e -> {
            this.model.stopServer(this.view::appendLog);
            this.view.getStartButton().setEnabled(true);
            this.view.getStopButton().setEnabled(false);
        });

        this.view.getExitButton().addActionListener(e -> System.exit(0));
    }

    public static void main(String[] args) {
        FTPServerModel model = new FTPServerModel(5000);
        FTPServerView view = new FTPServerView();
        new FTPServerController(model, view);
    }
}