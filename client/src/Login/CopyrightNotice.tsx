/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const CopyrightNotice = styled.span`
  color: ${({theme}) => theme.colors.text.copyrightNotice};
  font-size: 12px;
  padding-bottom: 10px;
`;

export {CopyrightNotice};
