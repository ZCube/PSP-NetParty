/*
Copyright (C) 2011 monte

This file is part of PSP NetParty.

PSP NetParty is free software: you can redistribute it and/or modify
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
package pspnetparty.client.swt;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

public class RoomDeleteDialog extends Dialog {

	public enum Selection {
		LOGOUT, DESTROY, CANCEL
	};

	private Selection selection = Selection.CANCEL;

	public RoomDeleteDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("방을 어떻게 합니까? ");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		return parent;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.horizontalSpacing = 10;
		layout.verticalSpacing = 12;
		layout.marginHeight = 10;
		container.setLayout(layout);

		Button logoutOnlyButton = new Button(container, SWT.PUSH);
		logoutOnlyButton.setText("방을 빠진다");
		logoutOnlyButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		logoutOnlyButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				selection = Selection.LOGOUT;
				close();
			}
		});

		Label logoutOnlyLabel = new Label(container, SWT.NONE);
		logoutOnlyLabel.setText("자동적으로 다른 사람에게 방주가 위양 됩니다");

		Button destroyRoomButton = new Button(container, SWT.PUSH);
		destroyRoomButton.setText("방을 해산한다");
		destroyRoomButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		destroyRoomButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				selection = Selection.DESTROY;
				close();
			}
		});

		Label destroyRoomLabel = new Label(container, SWT.NONE);
		destroyRoomLabel.setText("방을 삭제합니다. 전원 로그아웃 됩니다. ");

		Button cancelButton = new Button(container, SWT.PUSH);
		cancelButton.setText("캔슬한다");
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		cancelButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				selection = Selection.CANCEL;
				close();
			}
		});

		return parent;
	}

	public Selection getSelection() {
		return selection;
	}
}
