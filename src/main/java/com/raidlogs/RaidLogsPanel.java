package com.raidlogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.ui.PluginPanel;

class RaidLogsPanel extends PluginPanel
{
	private final JLabel status = new JLabel("Waiting for Theatre of Blood");
	private final JLabel error = new JLabel();
	private final JComboBox<String> raids = new JComboBox<>();
	private final JComboBox<String> encounters = new JComboBox<>();
	private final JButton analyze = new JButton("Open analysis");
	private final Map<String, RaidLog.Snapshot> saved = new LinkedHashMap<>();
	private final List<String> sessions = new ArrayList<>();
	private final List<JDialog> dialogs = new ArrayList<>();
	private boolean updating;

	RaidLogsPanel(Runnable reload)
	{
		add(new JLabel("Raid Logs · Theatre of Blood"));
		add(status);
		add(error);
		add(raids);
		add(encounters);
		add(analyze);
		JButton refresh = new JButton("Reload saved raids");
		refresh.addActionListener(event -> reload.run());
		add(refresh);
		JTextArea help = new JTextArea("Analysis unlocks after encounter completion. Raid totals unlock after raid completion. Files stay in .runelite/raid-logs.");
		help.setEditable(false);
		help.setLineWrap(true);
		help.setWrapStyleWord(true);
		help.setOpaque(false);
		add(help);
		raids.addActionListener(event -> selectRaid());
		encounters.addActionListener(event -> updateButton());
		analyze.addActionListener(event -> openAnalysis());
		analyze.setEnabled(false);
	}

	void status(String text)
	{
		status.setText(text);
	}

	void error(String text)
	{
		error.setText("Save/connection notice");
		error.setToolTipText(text);
	}

	void accept(List<RaidLog.Snapshot> snapshots)
	{
		for (RaidLog.Snapshot snapshot : snapshots)
		{
			saved.put(snapshot.getSession(), snapshot);
		}
		rebuild();
	}

	void current(RaidLog.Snapshot snapshot)
	{
		saved.put(snapshot.getSession(), snapshot);
		rebuild();
	}

	private void rebuild()
	{
		String selected = raids.getSelectedIndex() < 0 ? null : sessions.get(raids.getSelectedIndex());
		updating = true;
		raids.removeAllItems();
		sessions.clear();
		saved.values().stream().sorted(java.util.Comparator.comparing(RaidLog.Snapshot::getStarted).reversed())
			.forEach(snapshot ->
			{
				sessions.add(snapshot.getSession());
				raids.addItem(snapshot.getStarted().replace('T', ' ') + " · " + snapshot.getPlayer());
			});
		if (selected != null && sessions.contains(selected))
		{
			raids.setSelectedIndex(sessions.indexOf(selected));
		}
		updating = false;
		selectRaid();
	}

	private RaidLog.Snapshot selectedRaid()
	{
		int index = raids.getSelectedIndex();
		return index < 0 ? null : saved.get(sessions.get(index));
	}

	private void selectRaid()
	{
		if (updating)
		{
			return;
		}
		encounters.removeAllItems();
		RaidLog.Snapshot raid = selectedRaid();
		if (raid != null)
		{
			encounters.addItem(raid.isCompleted() ? "Whole raid" : "Whole raid · locked");
			for (EncounterLog.Snapshot encounter : raid.getEncounters())
			{
				encounters.addItem(encounter.getAttempt() + ". " + encounter.getRoom().displayName
					+ (encounter.isCompleted() ? "" : " · locked"));
			}
		}
		updateButton();
	}

	private void updateButton()
	{
		RaidLog.Snapshot raid = selectedRaid();
		int index = encounters.getSelectedIndex();
		analyze.setEnabled(AnalysisAccess.allowed(raid, index));
	}

	private void openAnalysis()
	{
		updateButton();
		if (!analyze.isEnabled())
		{
			return;
		}
		RaidLog.Snapshot raid = selectedRaid();
		int index = encounters.getSelectedIndex();
		List<EncounterLog.Snapshot> rooms = index == 0 ? raid.getEncounters()
			: java.util.Collections.singletonList(raid.getEncounters().get(index - 1));
		Map<String, long[]> totals = new LinkedHashMap<>();
		long other = 0;
		long misc = 0;
		int unmatched = 0;
		boolean partial = false;
		DefaultTableModel model = new DefaultTableModel(new String[]{"Encounter", "Player", "Tick", "Direction",
			"Damage", "Enemy", "Weapon", "Attack / cause", "Evidence", "Report"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int column) { return false; }
		};
		for (EncounterLog.Snapshot room : rooms)
		{
			other += room.getOtherObserved() - room.getOtherMatched();
			misc += room.getMiscellaneous();
			unmatched += room.getUnmatchedSelfReports();
			partial |= room.isPartial();
			for (EncounterLog.Entry entry : room.getEntries())
			{
				DamageHit hit = entry.getHit();
				String owner = hit.getKind() == DamageHit.Kind.THRALL ? "Misc: thralls" : entry.getPlayer();
				long[] total = totals.computeIfAbsent(owner, ignored -> new long[2]);
				total[hit.getDirection() == DamageHit.Direction.DEALT ? 0 : 1] += hit.getAmount();
				model.addRow(new Object[]{room.getRoom().displayName, owner, hit.getTick(), hit.getDirection(),
					hit.getAmount(), hit.getNpcName(), hit.getWeaponName(), hit.getAttack(), hit.getEvidence(),
					entry.isSelfReported() ? "Party self-report" : "Local observation"});
			}
		}
		StringBuilder summary = new StringBuilder("Player / category                 Dealt      Taken\n\n");
		totals.forEach((player, total) -> summary.append(String.format("%-32s %8d %10d%n", player, total[0], total[1])));
		summary.append("\nOther players / unmatched observed damage: ").append(other);
		summary.append("\nMiscellaneous unowned damage: ").append(misc);
		summary.append("\nParty dealt hits without a local match: ").append(unmatched);
		summary.append("\n\nAmounts are observed hitsplats, not XP estimates. Player totals include unresolved\n")
			.append("direct/indirect damage; only separately identified thralls move to Misc.\n")
			.append("Unknown weapons and attack causes are intentionally left unresolved.\n")
			.append("Other damage is a residual of this client's observations. Unmatched party reports\n")
			.append("may overlap that residual; these columns must not be summed as an exact grand total.\n")
			.append("Ticks are relative to each recorder's encounter start; partial recordings may differ.\n");
		if (partial)
		{
			summary.append("\nPARTIAL COVERAGE: recording started late, was interrupted, or lost synchronization.\n");
		}
		JTextArea text = new JTextArea(summary.toString());
		text.setEditable(false);
		text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
		JTable table = new JTable(model);
		table.setAutoCreateRowSorter(true);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		for (int column = 0; column < table.getColumnCount(); column++)
		{
			table.getColumnModel().getColumn(column).setPreferredWidth(column == 8 ? 300 : 140);
		}
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Totals", new JScrollPane(text));
		tabs.addTab("Damage log", new JScrollPane(table));
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Raid Logs · " + encounters.getSelectedItem());
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.add(tabs, BorderLayout.CENTER);
		dialog.setSize(new Dimension(1100, 620));
		dialog.setLocationRelativeTo(this);
		dialogs.add(dialog);
		dialog.setVisible(true);
	}

	void close()
	{
		dialogs.forEach(JDialog::dispose);
		dialogs.clear();
	}
}
